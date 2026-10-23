package com.mercuriusxeno.goo.tools.workbench;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;

/**
 * The Values tab's exchanges over the posted model and the inventory dump: resolve, uncovered, family and
 * hover (decision workbench-authors-base-values-whole).
 */
final class ValuesJson {
    private static final String ID = "id";
    private static final String ITEMS = "items";
    private static final String NAME = "name";
    private static final String GRID = "grid";
    private static final String COUNT = "count";
    private static final String RATIOS = "ratios";
    private static final String VALUE = "value";
    private static final String TAGS = "tags";

    private static Path cachedFile;
    private static FileTime cachedTime;
    private static InventoryView cachedView;

    private ValuesJson() {
    }

    /**
     * @param file the inventory dump
     * @return its view, read again only when the file changes
     * @throws IOException where no inventory stands or it cannot be read
     */
    static synchronized InventoryView inventory(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IOException("No inventory stands at " + file);
        }
        FileTime modified = Files.getLastModifiedTime(file);
        if (!file.equals(cachedFile) || !modified.equals(cachedTime)) {
            cachedView = InventoryView.parse(Files.readString(file));
            cachedFile = file;
            cachedTime = modified;
        }
        return cachedView;
    }

    /**
     * @param sent the posted JSON
     * @return the item id it names
     */
    static String idFrom(String sent) {
        return JsonParser.parseString(sent).getAsJsonObject().get(ID).getAsString();
    }

    /**
     * @param model     the model
     * @param inventory the inventory
     * @return the items nothing covers
     * @throws IOException where the model's text cannot be read
     */
    static String uncovered(BaseValuesModel model, InventoryView inventory) throws IOException {
        List<String> items = Valuations.uncovered(model, inventory);
        JsonArray array = new JsonArray();
        items.forEach(array::add);
        JsonObject root = new JsonObject();
        root.add(ITEMS, array);
        return root.toString();
    }

    /**
     * @param model     the model
     * @param inventory the inventory
     * @param id        the item's id
     * @return the item's hover card
     */
    static String hover(BaseValuesModel model, InventoryView inventory, String id) {
        Valuations.Hover hover = Valuations.hover(model, inventory, id);
        JsonObject root = new JsonObject();
        root.addProperty(ID, id);
        root.addProperty(NAME, inventory.nameOf(id));
        JsonArray grid = new JsonArray();
        hover.grid().forEach(grid::add);
        root.add(GRID, grid);
        root.addProperty(COUNT, hover.count());
        JsonArray ratios = new JsonArray();
        hover.ratios().forEach(ratios::add);
        root.add(RATIOS, ratios);
        root.addProperty(VALUE, hover.value());
        JsonArray tags = new JsonArray();
        hover.tags().forEach(tags::add);
        root.add(TAGS, tags);
        return root.toString();
    }
}
