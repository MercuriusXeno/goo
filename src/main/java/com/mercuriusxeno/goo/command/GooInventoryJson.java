package com.mercuriusxeno.goo.command;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Writes a {@link GooInventory} as the JSON the values workbench reads: one array per section, each
 * entry keyed by its namespaced id, in the order the listing gave
 * (decision inventory-dump-from-a-gametest-and-a-command).
 */
public final class GooInventoryJson {
    public static final String ITEMS = "items";
    public static final String BLOCKS = "blocks";
    public static final String ITEM_TAGS = "item_tags";
    public static final String BLOCK_TAGS = "block_tags";
    public static final String GOO_TYPES = "goo_types";
    public static final String RECIPES = "recipes";

    private static final String ID = "id";
    private static final String NAME = "name";
    private static final String IMAGE = "image";
    private static final String MEMBERS = "members";
    private static final String TYPE = "type";
    private static final String OUTPUT = "output";
    private static final String COUNT = "count";
    private static final String SLOTS = "slots";
    private static final String ALTERNATIVES = "alternatives";
    private static final String TAG = "tag";
    private static final String CONTAINERS = "containers";
    private static final String PATTERN = "pattern";
    private static final String KEY = "key";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private GooInventoryJson() {
    }

    /**
     * @param inventory the listing to write
     * @return the JSON text
     */
    public static String toJson(GooInventory inventory) {
        JsonObject root = new JsonObject();
        root.add(ITEMS, arrayOf(inventory.items(), GooInventoryJson::namedEntry));
        root.add(BLOCKS, arrayOf(inventory.blocks(), GooInventoryJson::namedEntry));
        root.add(ITEM_TAGS, arrayOf(inventory.itemTags(), GooInventoryJson::tagEntry));
        root.add(BLOCK_TAGS, arrayOf(inventory.blockTags(), GooInventoryJson::tagEntry));
        root.add(GOO_TYPES, arrayOf(inventory.gooTypes(), GooInventoryJson::gooType));
        root.add(RECIPES, arrayOf(inventory.recipes(), GooInventoryJson::recipeEntry));
        return GSON.toJson(root);
    }

    private static <T> JsonArray arrayOf(Collection<T> entries, Function<T, JsonObject> writer) {
        JsonArray array = new JsonArray();
        entries.forEach(entry -> array.add(writer.apply(entry)));
        return array;
    }

    private static JsonArray strings(List<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }

    private static JsonObject namedEntry(GooInventory.NamedEntry entry) {
        JsonObject json = new JsonObject();
        json.addProperty(ID, entry.id());
        json.addProperty(NAME, entry.name());
        if (entry.image() != null) {
            json.addProperty(IMAGE, entry.image());
        }
        return json;
    }

    private static JsonObject tagEntry(GooInventory.TagEntry entry) {
        JsonObject json = new JsonObject();
        json.addProperty(ID, entry.id());
        json.add(MEMBERS, strings(entry.members()));
        return json;
    }

    private static JsonObject gooType(String id) {
        JsonObject json = new JsonObject();
        json.addProperty(ID, id);
        return json;
    }

    private static JsonObject slotEntry(GooInventory.SlotEntry slot) {
        JsonObject json = new JsonObject();
        json.add(ALTERNATIVES, strings(slot.alternatives()));
        if (slot.tag() != null) {
            json.addProperty(TAG, slot.tag());
        }
        return json;
    }

    private static JsonObject recipeEntry(GooInventory.RecipeEntry recipe) {
        JsonObject json = new JsonObject();
        json.addProperty(ID, recipe.id());
        json.addProperty(TYPE, recipe.type());
        if (recipe.output() != null) {
            json.addProperty(OUTPUT, recipe.output());
            json.addProperty(COUNT, recipe.count());
        }
        json.add(SLOTS, arrayOf(recipe.slots(), GooInventoryJson::slotEntry));
        JsonObject containers = new JsonObject();
        recipe.containers().forEach(containers::addProperty);
        json.add(CONTAINERS, containers);
        if (recipe.grid() != null) {
            json.add(PATTERN, strings(recipe.grid().pattern()));
            JsonObject key = new JsonObject();
            for (Map.Entry<String, GooInventory.SlotEntry> symbol : recipe.grid().key().entrySet()) {
                key.add(symbol.getKey(), slotEntry(symbol.getValue()));
            }
            json.add(KEY, key);
        }
        return json;
    }
}
