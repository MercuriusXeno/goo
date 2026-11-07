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
import java.nio.file.StandardCopyOption;
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
    private static final String GOO_NAMESPACE = "goo";

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
        for (Map.Entry<Identifier, Path> draft : filesUnder(folder).entrySet()) {
            drafts.put(draft.getKey(), parsed(draft.getValue()));
        }
        return drafts;
    }

    /**
     * @param abilities the live goo_abilities folder of the goo namespace, which may not stand yet
     * @return every ability file standing in it, by id, as written
     * @throws IOException where a file cannot be read
     */
    public static Map<Identifier, JsonObject> readLive(Path abilities) throws IOException {
        Map<Identifier, JsonObject> live = new HashMap<>();
        for (Path file : jsonFiles(abilities)) {
            live.put(Identifier.fromNamespaceAndPath(GOO_NAMESPACE, pathOf(abilities.relativize(file))),
                    parsed(file));
        }
        return live;
    }

    /**
     * Moves each draft over its live file, the drafts folder left holding none, as Promote renames base_values'
     * draft over its live file (decision ability-drafts-promote-per-file-like-base-values).
     *
     * @param drafts    the drafts folder
     * @param abilities the live goo_abilities folder of the goo namespace
     * @return the promoted abilities' ids
     * @throws IOException where a draft cannot be moved
     */
    public static List<Identifier> promote(Path drafts, Path abilities) throws IOException {
        Map<Identifier, Path> standing = filesUnder(drafts);
        for (Map.Entry<Identifier, Path> draft : standing.entrySet()) {
            Path live = liveFileOf(abilities, draft.getKey());
            Files.createDirectories(live.getParent());
            Files.move(draft.getValue(), live, StandardCopyOption.REPLACE_EXISTING);
        }
        return List.copyOf(standing.keySet());
    }

    /**
     * Deletes every draft, so each ability reads its live file again: the pack's where it holds one, else the jar's.
     *
     * @param drafts the drafts folder
     * @throws IOException where a draft cannot be deleted
     */
    public static void discard(Path drafts) throws IOException {
        for (Path draft : filesUnder(drafts).values()) {
            Files.delete(draft);
        }
    }

    /**
     * @param abilities the live goo_abilities folder of the goo namespace
     * @param id        an ability's id
     * @return its live file: in that folder for the goo namespace, in the same pack's folder of its own namespace
     *     otherwise
     */
    public static Path liveFileOf(Path abilities, Identifier id) {
        Path folder = GOO_NAMESPACE.equals(id.getNamespace()) ? abilities
                : abilities.getParent().resolveSibling(id.getNamespace()).resolve(abilities.getFileName());
        return folder.resolve(id.getPath() + JSON_SUFFIX);
    }

    // Each draft file under the folder, by its ability's id: the namespace's folder, then the id's path.
    private static Map<Identifier, Path> filesUnder(Path folder) throws IOException {
        Map<Identifier, Path> files = new HashMap<>();
        for (Path file : jsonFiles(folder)) {
            Path relative = folder.relativize(file);
            if (relative.getNameCount() >= NAMESPACE_AND_PATH) {
                files.put(Identifier.fromNamespaceAndPath(relative.getName(0).toString(),
                        pathOf(relative.subpath(1, relative.getNameCount()))), file);
            }
        }
        return files;
    }

    private static List<Path> jsonFiles(Path folder) throws IOException {
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> walked = Files.walk(folder)) {
            return walked.filter(Files::isRegularFile).filter(file -> file.toString().endsWith(JSON_SUFFIX))
                    .toList();
        }
    }

    // A file's path below its namespace's folder, as the id's path spells it.
    private static String pathOf(Path relative) {
        String path = relative.toString().replace(relative.getFileSystem().getSeparator(), PATH_SEPARATOR);
        return path.substring(0, path.length() - JSON_SUFFIX.length());
    }

    private static JsonObject parsed(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
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
