package com.mercuriusxeno.goo.ability;

import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.Goo;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Test helper that reads the shipped ability JSON from the classpath and decodes each
 * file under the id its filename gives, as {@link AbilityLoader} does.
 */
public final class AbilityJson {

    /** Classpath directory holding the shipped ability files. */
    public static final String ABILITIES_DIR = "data/goo/goo_abilities";

    private static final String JSON_SUFFIX = ".json";

    private AbilityJson() {
    }

    /**
     * The id an ability file loads under: the goo namespace and the filename without its suffix.
     *
     * @param fileName the file's name, such as {@code nether_black_hole.json}
     * @return the ability id
     */
    public static Identifier idOf(String fileName) {
        return Identifier.fromNamespaceAndPath(Goo.MODID, fileName.substring(0, fileName.length() - JSON_SUFFIX.length()));
    }

    /**
     * The id an ability loads under from its classpath path, such as
     * {@code data/goo/goo_abilities/rock_tunnel.json} for {@code goo:rock_tunnel}.
     *
     * @param resource the classpath path, ending {@code data/<ns>/goo_abilities/<name>.json}
     * @return the ability id
     */
    public static Identifier idOfResource(String resource) {
        String[] parts = resource.split("/");
        String name = parts[parts.length - 1];
        return Identifier.fromNamespaceAndPath(parts[parts.length - 3],
                name.substring(0, name.length() - JSON_SUFFIX.length()));
    }

    /**
     * Every shipped ability file, sorted by name.
     *
     * @return the ability file paths
     */
    public static List<Path> files() {
        URL dir = AbilityJson.class.getClassLoader().getResource(ABILITIES_DIR);
        if (dir == null) {
            throw new IllegalStateException("Classpath holds no " + ABILITIES_DIR);
        }
        try (Stream<Path> files = Files.list(Path.of(dir.toURI()))) {
            return files.filter(p -> p.getFileName().toString().endsWith(JSON_SUFFIX)).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Decodes one shipped ability by name.
     *
     * @param name the ability's filename without its suffix, such as {@code nether_black_hole}
     * @return the decoded definition
     */
    public static AbilityDefinition decode(String name) {
        String fileName = name + JSON_SUFFIX;
        InputStream stream = AbilityJson.class.getClassLoader().getResourceAsStream(ABILITIES_DIR + "/" + fileName);
        if (stream == null) {
            throw new IllegalStateException("Classpath holds no " + ABILITIES_DIR + "/" + fileName);
        }
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return decode(fileName, reader);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Decodes one ability file from disk.
     *
     * @param file the ability file
     * @return the decoded definition
     */
    public static AbilityDefinition decode(Path file) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return decode(file.getFileName().toString(), reader);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static AbilityDefinition decode(String fileName, Reader reader) {
        return AbilityDefinition.codecFor(idOf(fileName))
                .parse(JsonOps.INSTANCE, JsonParser.parseReader(reader))
                .getOrThrow(message -> new IllegalStateException(fileName + ": " + message));
    }
}
