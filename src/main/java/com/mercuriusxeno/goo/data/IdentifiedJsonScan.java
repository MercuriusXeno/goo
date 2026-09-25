package com.mercuriusxeno.goo.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.Goo;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.neoforge.common.conditions.ConditionalOps;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Scans a datapack directory of JSON files, decoding each with a codec built for
 * that file's id, so a record carries its id from construction (decision
 * delete-dead-fold-mirrors). Mirrors vanilla's
 * {@code SimpleJsonResourceReloadListener.scanDirectory}, NeoForge conditions included.
 */
public final class IdentifiedJsonScan {

    private static final String LOG_SKIPPED = "Skipping data file '{}' from '{}': its conditions were not met";
    private static final String LOG_UNPARSED = "Couldn't parse data file '{}' from '{}': {}";
    private static final String DUPLICATE_ID = "Duplicate data file ignored with ID ";

    private IdentifiedJsonScan() {
    }

    /**
     * Decodes every JSON file the lister matches, keyed by file id.
     *
     * @param manager    the resource manager to list from
     * @param lister     maps resource paths to file ids
     * @param ops        the JSON ops, conditional ops for a reload
     * @param codecForId builds the codec that decodes the file with the given id
     * @param <T>        the decoded record type
     * @return the decoded records by file id; a file that fails to parse is logged and left out
     */
    public static <T> Map<Identifier, T> scan(ResourceManager manager, FileToIdConverter lister,
                                              DynamicOps<JsonElement> ops,
                                              Function<Identifier, Codec<T>> codecForId) {
        Map<Identifier, T> decoded = new HashMap<>();
        for (Map.Entry<Identifier, Resource> entry : lister.listMatchingResources(manager).entrySet()) {
            Identifier path = entry.getKey();
            Identifier fileId = lister.fileToId(path);
            try (Reader reader = entry.getValue().openAsReader()) {
                decodeOne(path, fileId, JsonParser.parseReader(reader), ops, codecForId.apply(fileId), decoded);
            } catch (IllegalArgumentException | IOException | JsonParseException e) {
                Goo.LOGGER.error(LOG_UNPARSED, fileId, path, e.getMessage());
            }
        }
        return decoded;
    }

    /**
     * Decodes one file under its conditions and adds it to the map.
     *
     * @param path    the file's resource path, for the log
     * @param fileId  the id the file loads under
     * @param json    the file's parsed JSON
     * @param ops     the JSON ops
     * @param codec   the codec built for this file's id
     * @param decoded the map the decoded record joins
     * @param <T>     the decoded record type
     */
    private static <T> void decodeOne(Identifier path, Identifier fileId, JsonElement json,
                                      DynamicOps<JsonElement> ops, Codec<T> codec, Map<Identifier, T> decoded) {
        ConditionalOps.createConditionalCodec(codec).parse(ops, json)
                .ifSuccess(value -> {
                    if (value.isEmpty()) {
                        Goo.LOGGER.debug(LOG_SKIPPED, fileId, path);
                    } else if (decoded.putIfAbsent(fileId, value.get()) != null) {
                        throw new IllegalStateException(DUPLICATE_ID + fileId);
                    }
                })
                .ifError(error -> Goo.LOGGER.error(LOG_UNPARSED, fileId, path, error.message()));
    }
}
