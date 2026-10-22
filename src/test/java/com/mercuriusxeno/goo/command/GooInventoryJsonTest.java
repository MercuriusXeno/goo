package com.mercuriusxeno.goo.command;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the inventory writer over a fake registry listing: each id lands under its section, and
 * tag members, recipe slots and the shaped grid keep the order the listing gave them.
 */
class GooInventoryJsonTest {

    private static final GooInventory.SlotEntry IRON = new GooInventory.SlotEntry(
            List.of("minecraft:iron_ingot"), null);
    private static final GooInventory.SlotEntry NUGGET = new GooInventory.SlotEntry(
            List.of("minecraft:iron_nugget"), null);

    private static final GooInventory LISTING = new GooInventory(
            List.of(new GooInventory.NamedEntry("minecraft:iron_nugget", "Iron Nugget", "minecraft/iron_nugget.png"),
                    new GooInventory.NamedEntry("othermod:widget", "Widget", null)),
            List.of(new GooInventory.NamedEntry("minecraft:stone", "Stone", null)),
            List.of(new GooInventory.TagEntry("minecraft:logs", List.of("minecraft:oak_log", "minecraft:birch_log"))),
            List.of(new GooInventory.TagEntry("minecraft:mineable/pickaxe", List.of("minecraft:stone"))),
            List.of("metal", "othermod:sludge"),
            List.of(new GooInventory.RecipeEntry("minecraft:iron_chain", "minecraft:crafting",
                    "minecraft:iron_chain", 1, List.of(NUGGET, IRON, NUGGET), Map.of(),
                    new GooInventory.ShapedGrid(List.of("N", "I", "N"), Map.of("I", IRON, "N", NUGGET)))));

    private static JsonObject written() {
        return JsonParser.parseString(GooInventoryJson.toJson(LISTING)).getAsJsonObject();
    }

    private static List<String> idsUnder(JsonObject root, String section) {
        return root.getAsJsonArray(section).asList().stream()
                .map(entry -> entry.getAsJsonObject().get("id").getAsString()).toList();
    }

    private static List<String> strings(JsonArray array) {
        return array.asList().stream().map(JsonElement::getAsString).toList();
    }

    /**
     * Every id the listing holds lands under its own section, a modded id beside minecraft's.
     */
    @Test
    void eachIdLandsUnderItsSection() {
        JsonObject root = written();
        assertEquals(List.of("minecraft:iron_nugget", "othermod:widget"), idsUnder(root, GooInventoryJson.ITEMS));
        assertEquals(List.of("minecraft:stone"), idsUnder(root, GooInventoryJson.BLOCKS));
        assertEquals(List.of("minecraft:logs"), idsUnder(root, GooInventoryJson.ITEM_TAGS));
        assertEquals(List.of("minecraft:mineable/pickaxe"), idsUnder(root, GooInventoryJson.BLOCK_TAGS));
        assertEquals(List.of("metal", "othermod:sludge"), idsUnder(root, GooInventoryJson.GOO_TYPES));
        assertEquals(List.of("minecraft:iron_chain"), idsUnder(root, GooInventoryJson.RECIPES));
    }

    /**
     * Tag members keep the listing's order.
     */
    @Test
    void tagMembersKeepTheirOrder() {
        JsonObject logs = written().getAsJsonArray(GooInventoryJson.ITEM_TAGS).get(0).getAsJsonObject();
        assertEquals(List.of("minecraft:oak_log", "minecraft:birch_log"), strings(logs.getAsJsonArray("members")));
    }

    /**
     * An item carries its name and the image path the page draws it from.
     */
    @Test
    void itemCarriesNameAndImage() {
        JsonObject nugget = written().getAsJsonArray(GooInventoryJson.ITEMS).get(0).getAsJsonObject();
        assertEquals("Iron Nugget", nugget.get("name").getAsString());
        assertEquals("minecraft/iron_nugget.png", nugget.get("image").getAsString());
    }

    /**
     * A shaped recipe carries its output, its slots in order and the pattern and key the page draws the grid from.
     */
    @Test
    void shapedRecipeCarriesItsGrid() {
        JsonObject chain = written().getAsJsonArray(GooInventoryJson.RECIPES).get(0).getAsJsonObject();
        assertEquals("minecraft:iron_chain", chain.get("output").getAsString());
        assertEquals(1, chain.get("count").getAsInt());
        assertEquals(List.of("minecraft:iron_nugget"),
                strings(chain.getAsJsonArray("slots").get(0).getAsJsonObject().getAsJsonArray("alternatives")));
        assertEquals(List.of("N", "I", "N"), strings(chain.getAsJsonArray("pattern")));
        JsonObject key = chain.getAsJsonObject("key");
        assertTrue(key.has("I"));
        assertEquals(List.of("minecraft:iron_ingot"),
                strings(key.getAsJsonObject("I").getAsJsonArray("alternatives")));
    }
}
