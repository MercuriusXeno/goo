package com.mercuriusxeno.goo.workbench;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The ability drafts on disk: one JSON file per edited ability under the drafts folder, at its namespace's folder and
 * its id's path, as goo_abilities lays the live files (decision ability-drafts-promote-per-file-like-base-values).
 */
public final class AbilityDrafts {
    private static final String JSON_SUFFIX = ".json";
    private static final String PATH_SEPARATOR = "/";
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String LINE_END = "\n";
    // A draft stands at its namespace's folder and at least one segment of its path below.
    private static final int NAMESPACE_AND_PATH = 2;

    private AbilityDrafts() {
    }

    /**
     * @param folder the drafts folder
     * @param id     an ability's id
     * @return the draft's file
     */
    public static Path fileOf(Path folder, Identifier id) {
        return folder.resolve(id.getNamespace()).resolve(id.getPath() + JSON_SUFFIX);
    }

    /**
     * @param folder the drafts folder, which may not stand yet
     * @return every draft standing, by its ability's id
     * @throws IOException where a draft cannot be read
     */
    public static Map<Identifier, JsonObject> read(Path folder) throws IOException {
        Map<Identifier, JsonObject> drafts = new HashMap<>();
        if (!Files.isDirectory(folder)) {
            return drafts;
        }
        List<Path> files;
        try (Stream<Path> walked = Files.walk(folder)) {
            files = walked.filter(Files::isRegularFile).filter(file -> file.toString().endsWith(JSON_SUFFIX))
                    .toList();
        }
        for (Path file : files) {
            Path relative = folder.relativize(file);
            if (relative.getNameCount() < NAMESPACE_AND_PATH) {
                continue;
            }
            String namespace = relative.getName(0).toString();
            String path = relative.subpath(1, relative.getNameCount()).toString().replace(file.getFileSystem()
                    .getSeparator(), PATH_SEPARATOR);
            Identifier id = Identifier.fromNamespaceAndPath(namespace,
                    path.substring(0, path.length() - JSON_SUFFIX.length()));
            drafts.put(id, JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject());
        }
        return drafts;
    }

    /**
     * Writes an ability's draft, its folders first.
     *
     * @param folder the drafts folder
     * @param id     the ability's id
     * @param json   the ability's JSON as edited
     * @throws IOException where the draft cannot be written
     */
    public static void write(Path folder, Identifier id, JsonObject json) throws IOException {
        Path file = fileOf(folder, id);
        Files.createDirectories(file.getParent());
        Files.writeString(file, PRETTY.toJson(json) + LINE_END, StandardCharsets.UTF_8);
    }
}
