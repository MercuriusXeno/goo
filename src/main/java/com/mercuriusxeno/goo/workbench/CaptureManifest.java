package com.mercuriusxeno.goo.workbench;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The record of which items a slot capture has drawn into the image folder and when, kept as manifest.json beside
 * that folder, so a capture run draws only the items the inventory lists that the manifest lacks.
 * decision images-are-slot-captures-taken-on-detection
 *
 * @param captured each captured item id and when its capture was written
 */
public record CaptureManifest(Map<String, Instant> captured) {
    /**
     * A manifest naming no capture.
     */
    public static final CaptureManifest EMPTY = new CaptureManifest(Map.of());
    private static final String FILE_NAME = "manifest.json";
    private static final String CAPTURED = "captured";
    private static final String ITEMS = "items";
    private static final String ID = "id";

    /**
     * @param captured each captured item id and when its capture was written
     */
    public CaptureManifest {
        captured = Collections.unmodifiableMap(new TreeMap<>(captured));
    }

    /**
     * @param imageFolder the folder the captures land in
     * @return manifest.json beside the folder
     */
    public static Path fileBeside(Path imageFolder) {
        return imageFolder.resolveSibling(FILE_NAME);
    }

    /**
     * @param json the manifest's text
     * @return the manifest it names, or the empty manifest where the text is unreadable, so every item is captured
     *         anew
     */
    public static CaptureManifest parse(String json) {
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject() || !root.getAsJsonObject().has(CAPTURED)) {
                return EMPTY;
            }
            Map<String, Instant> captured = new TreeMap<>();
            root.getAsJsonObject().getAsJsonObject(CAPTURED).entrySet()
                    .forEach(entry -> captured.put(entry.getKey(), Instant.parse(entry.getValue().getAsString())));
            return new CaptureManifest(captured);
        } catch (JsonParseException | IllegalStateException | DateTimeParseException e) {
            return EMPTY;
        }
    }

    /**
     * @param file the manifest file
     * @return the manifest it holds, or the empty manifest where none stands
     * @throws IOException where the file stands but cannot be read
     */
    public static CaptureManifest read(Path file) throws IOException {
        return Files.isRegularFile(file) ? parse(Files.readString(file, StandardCharsets.UTF_8)) : EMPTY;
    }

    /**
     * @param inventoryJson the inventory file's text
     * @return the id of every item its items section lists, in its order
     */
    public static List<String> itemIdsOf(String inventoryJson) {
        JsonObject root = JsonParser.parseString(inventoryJson).getAsJsonObject();
        if (!root.has(ITEMS)) {
            return List.of();
        }
        return root.getAsJsonArray(ITEMS).asList().stream()
                .map(item -> item.getAsJsonObject().get(ID).getAsString()).toList();
    }

    /**
     * @param id   the captured item's id
     * @param when when its capture was written
     * @return this manifest naming the capture beside its own
     */
    public CaptureManifest withCapture(String id, Instant when) {
        Map<String, Instant> next = new TreeMap<>(captured);
        next.put(id, when);
        return new CaptureManifest(next);
    }

    /**
     * @param inventoryIds the item ids the inventory lists
     * @return the ids no capture covers, in the inventory's order
     */
    public List<String> missingFrom(List<String> inventoryIds) {
        return inventoryIds.stream().filter(id -> !captured.containsKey(id)).toList();
    }

    /**
     * @return the manifest as the text manifest.json holds
     */
    public String toJson() {
        JsonObject entries = new JsonObject();
        captured.forEach((id, when) -> entries.addProperty(id, when.toString()));
        JsonObject root = new JsonObject();
        root.add(CAPTURED, entries);
        return new GsonBuilder().setPrettyPrinting().create().toJson(root);
    }

    /**
     * @param file the manifest file
     * @throws IOException where the file cannot be written
     */
    public void write(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, toJson(), StandardCharsets.UTF_8);
    }
}
