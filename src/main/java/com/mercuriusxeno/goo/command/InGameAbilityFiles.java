package com.mercuriusxeno.goo.command;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.AbilityRegistrySource;
import com.mercuriusxeno.goo.data.IdentifiedJsonScan;
import com.mercuriusxeno.goo.network.AbilitySyncPayload;
import com.mercuriusxeno.goo.workbench.AbilityFiles;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.neoforged.neoforge.network.PacketDistributor;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;

/**
 * The running server's ability files, read from its resource stack as the ability loader reads them, and the live
 * apply of ability drafts over them: the server's ability registry rebuilt and every client resynced, on the server
 * thread, with no /reload, the ability counterpart of {@link ValuesManagers#applyLive}
 * (decision ability-drafts-promote-per-file-like-base-values).
 */
final class InGameAbilityFiles implements AbilityFiles {
    private static final FileToIdConverter LISTER = FileToIdConverter.json("goo_abilities");

    private final MinecraftServer server;

    InGameAbilityFiles(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public Map<Identifier, JsonObject> live() throws IOException {
        try {
            return server.submit(this::readLive).join();
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private Map<Identifier, JsonObject> readLive() {
        Map<Identifier, JsonObject> files = new HashMap<>();
        for (Map.Entry<Identifier, Resource> entry : LISTER.listMatchingResources(server.getResourceManager())
                .entrySet()) {
            try (Reader reader = entry.getValue().openAsReader()) {
                files.put(LISTER.fileToId(entry.getKey()), JsonParser.parseReader(reader).getAsJsonObject());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } catch (JsonParseException | IllegalStateException e) {
                // A file the loader cannot read either loads no ability, so the tab lists none for it.
                files.remove(LISTER.fileToId(entry.getKey()));
            }
        }
        return files;
    }

    @Override
    public void apply(Map<Identifier, JsonObject> drafts) {
        server.submit(() -> {
            Map<Identifier, AbilityDefinition> abilities = new HashMap<>(IdentifiedJsonScan.scan(
                    server.getResourceManager(), LISTER, JsonOps.INSTANCE, AbilityDefinition::codecFor));
            drafts.forEach((id, json) -> AbilityDefinition.codecFor(id).parse(JsonOps.INSTANCE, json)
                    .ifSuccess(ability -> abilities.put(id, ability)));
            ((AbilityRegistrySource) server.getServerResources().managers())
                    .holdAbilityRegistry(new AbilityRegistry(abilities));
            AbilitySyncPayload payload = AbilitySyncPayload.fromRegistry(AbilityRegistry.of(server));
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.connection.hasChannel(payload)) {
                    PacketDistributor.sendToPlayer(player, payload);
                }
            }
        }).join();
    }
}
