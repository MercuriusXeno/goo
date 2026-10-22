package com.mercuriusxeno.goo.tools.workbench;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.data.GooBaseValuesPreview;
import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the Values tab's writes and reads: every entry form the file supports, resolved by the game's loader;
 * the items nothing covers; and the hover card's grid, ratios and value
 * (decision workbench-authors-base-values-whole).
 */
class ValuationsTest {

    private static final String SMALL = """
            {
                "_constants": {
                    "copper_nugget": {"metal": 2},
                    "food": {"jelly": 3},
                    "iron_ingot": {"metal": 54}
                },
                "_groups": {},
                "_restricted": [],
                "_conversions": {},
                "_post_conversions": {}
            }
            """;

    private static BaseValuesModel shipped() throws IOException {
        return BaseValuesModel.parse(new String(BaseValuesModelTest.shippedBytes(), StandardCharsets.UTF_8));
    }

    private static Identifier id(String path) {
        return Identifier.withDefaultNamespace(path);
    }

    private static BaseValuesModel everyForm() {
        BaseValuesModel model = BaseValuesModel.parse(SMALL);
        model = model.withTopLevel("iron_nugget", ValueEntries.perType(Map.of("metal", "4 $copper_nugget.metal")));
        model = model.withTopLevel("iron_ingot", ValueEntries.expression("$iron_ingot"));
        model = model.withTopLevel("bucket", ValueEntries.expression("3 $iron_ingot"));
        model = model.withTopLevel("milk_bucket", ValueEntries.expression("bucket + $food"));
        model = model.withTopLevel("raw_iron", ValueEntries.ratio("1", "iron_ingot"));
        model = model.withTopLevel("bedrock", ValueEntries.denied());
        return ValueEntries.withRestricted(model, "milk_bucket");
    }

    /**
     * Each form, authored through the model and serialized, is what the game's loader reads.
     */
    @Test
    void everyEntryFormLoadsAsTheGameReadsIt() throws IOException {
        GooBaseValuesPreview.Loaded loaded = GooBaseValuesPreview.load(everyForm().serialize());
        Map<Identifier, GooValue> values = loaded.baseValues();
        assertEquals(GooFraction.of(8), values.get(id("iron_nugget")).exact(GooTypes.METAL));
        assertEquals(GooFraction.of(162), values.get(id("bucket")).exact(GooTypes.METAL));
        assertEquals(GooFraction.of(162), values.get(id("milk_bucket")).exact(GooTypes.METAL));
        assertEquals(GooFraction.of(3), values.get(id("milk_bucket")).exact(GooTypes.JELLY));
        assertEquals(GooFraction.of(54), values.get(id("raw_iron")).exact(GooTypes.METAL));
        assertTrue(loaded.denied().contains(id("bedrock")));
        assertTrue(loaded.restricted().contains(id("milk_bucket")));
    }

    /**
     * The resolve read answers each entry as the loader values it, and an unknown item as the lexer's error.
     */
    @Test
    void resolveAnswersTheLoaderAndTheLexer() throws IOException {
        BaseValuesModel model = everyForm().withTopLevel("pail", ValueEntries.expression("bucket + no_such_item"));
        JsonArray entries = JsonParser.parseString(Valuations.resolved(model)).getAsJsonObject()
                .getAsJsonArray("entries");
        JsonObject milk = entryNamed(entries, "milk_bucket");
        assertEquals("162", milk.getAsJsonObject("value").get("metal").getAsString());
        assertEquals("3", milk.getAsJsonObject("value").get("jelly").getAsString());
        assertTrue(milk.get("restricted").getAsBoolean());
        assertTrue(entryNamed(entries, "bedrock").get("denied").getAsBoolean());
        assertTrue(entryNamed(entries, "pail").get("error").getAsString().contains("no_such_item"));
    }

    private static JsonObject entryNamed(JsonArray entries, String key) {
        return entries.asList().stream().map(entry -> entry.getAsJsonObject())
                .filter(entry -> key.equals(entry.get("key").getAsString())).findFirst().orElseThrow();
    }

    /**
     * Against an inventory holding every id the shipped file names and one more, the one more is all that
     * nothing covers; dropping a member from its group uncovers it.
     */
    @Test
    void uncoveredListsWhatNothingCovers() throws IOException {
        BaseValuesModel model = shipped();
        List<String> inventory = new ArrayList<>(Valuations.namedIds(model));
        inventory.add("minecraft:not_valued_yet");
        assertEquals(List.of("minecraft:not_valued_yet"), Valuations.uncovered(model, inventory));

        GroupConvention.Group simpleLogs = GroupConvention.describe(model).stream()
                .filter(group -> "simple_logs".equals(group.name())).findFirst().orElseThrow();
        String dropped = simpleLogs.members().getFirst();
        BaseValuesModel without = GroupConvention.withGroup(model, "simple_logs",
                simpleLogs.members().subList(1, simpleLogs.members().size()));
        assertTrue(Valuations.uncovered(without, inventory).contains(dropped), dropped);
    }

    private static JsonObject shapedRecipe(String recipeId, String output, int count, List<String> pattern,
                                           Map<String, String> key) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("id", recipeId);
        recipe.addProperty("type", "minecraft:crafting");
        recipe.addProperty("output", output);
        recipe.addProperty("count", count);
        JsonArray slots = new JsonArray();
        for (String row : pattern) {
            for (char symbol : row.toCharArray()) {
                if (symbol != ' ') {
                    slots.add(slot(key.get(String.valueOf(symbol))));
                }
            }
        }
        recipe.add("slots", slots);
        recipe.add("containers", new JsonObject());
        JsonArray rows = new JsonArray();
        pattern.forEach(rows::add);
        recipe.add("pattern", rows);
        JsonObject keyJson = new JsonObject();
        key.forEach((symbol, item) -> keyJson.add(symbol, slot(item)));
        recipe.add("key", keyJson);
        return recipe;
    }

    private static JsonObject plainRecipe(String recipeId, String type, String output, int count, String input,
                                          int inputs) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("id", recipeId);
        recipe.addProperty("type", type);
        recipe.addProperty("output", output);
        recipe.addProperty("count", count);
        JsonArray slots = new JsonArray();
        for (int i = 0; i < inputs; i++) {
            slots.add(slot(input));
        }
        recipe.add("slots", slots);
        recipe.add("containers", new JsonObject());
        return recipe;
    }

    private static JsonObject slot(String item) {
        JsonObject slot = new JsonObject();
        JsonArray alternatives = new JsonArray();
        alternatives.add(item);
        slot.add("alternatives", alternatives);
        return slot;
    }

    private static String ironInventory() {
        JsonObject root = new JsonObject();
        JsonArray items = new JsonArray();
        for (String path : List.of("iron_chain", "iron_nugget", "iron_ingot", "raw_iron")) {
            JsonObject item = new JsonObject();
            item.addProperty("id", "minecraft:" + path);
            item.addProperty("name", path);
            items.add(item);
        }
        root.add("items", items);
        root.add("blocks", new JsonArray());
        JsonArray tags = new JsonArray();
        JsonObject chains = new JsonObject();
        chains.addProperty("id", "minecraft:chains");
        JsonArray members = new JsonArray();
        members.add("minecraft:iron_chain");
        chains.add("members", members);
        tags.add(chains);
        root.add("item_tags", tags);
        root.add("block_tags", new JsonArray());
        root.add("goo_types", new JsonArray());
        JsonArray recipes = new JsonArray();
        recipes.add(shapedRecipe("minecraft:iron_chain", "minecraft:iron_chain", 1, List.of("N", "I", "N"),
                Map.of("N", "minecraft:iron_nugget", "I", "minecraft:iron_ingot")));
        recipes.add(plainRecipe("minecraft:iron_ingot_from_nuggets", "minecraft:crafting", "minecraft:iron_ingot", 1,
                "minecraft:iron_nugget", 9));
        recipes.add(plainRecipe("minecraft:iron_nugget", "minecraft:crafting", "minecraft:iron_nugget", 9,
                "minecraft:iron_ingot", 1));
        recipes.add(plainRecipe("minecraft:iron_ingot_from_smelting_raw_iron", "minecraft:smelting",
                "minecraft:iron_ingot", 1, "minecraft:raw_iron", 1));
        root.add("recipes", recipes);
        return root.toString();
    }

    /**
     * A #tag entry values the tag's members from the inventory's tags, so the workbench resolves a real tag
     * with no game registry, as a tools JVM holds none.
     */
    @Test
    void tagEntryValuesTheInventoryTagsMembers() {
        BaseValuesModel model = BaseValuesModel.parse(SMALL).withTopLevel("#minecraft:logs",
                ValueEntries.perType(Map.of("leaf", "4")));
        JsonObject inventory = JsonParser.parseString(ironInventory()).getAsJsonObject();
        JsonObject logs = new JsonObject();
        logs.addProperty("id", "minecraft:logs");
        JsonArray members = new JsonArray();
        members.add("minecraft:oak_log");
        logs.add("members", members);
        inventory.getAsJsonArray("item_tags").add(logs);
        Valuations.Hover hover = Valuations.hover(model, InventoryView.parse(inventory.toString()), "minecraft:oak_log");
        assertEquals("leaf 4", hover.value());
    }

    /**
     * The chain's card draws its one-wide pattern centred, counts one result, reads the ratios through its
     * ingredients and values it at the shipped anchors.
     */
    @Test
    void hoverCardReadsTheChain() throws IOException {
        Valuations.Hover hover = Valuations.hover(shipped(), InventoryView.parse(ironInventory()),
                "minecraft:iron_chain");
        assertEquals(java.util.Arrays.asList(null, "minecraft:iron_nugget", null, null, "minecraft:iron_ingot", null,
                null, "minecraft:iron_nugget", null), hover.grid());
        assertEquals(1, hover.count());
        assertTrue(hover.ratios().contains("1 iron_chain = 11 iron_nugget"), hover.ratios().toString());
        assertTrue(hover.ratios().contains("1 iron_chain = 11/9 iron_ingot"), hover.ratios().toString());
        assertEquals("metal 12672", hover.value());
        assertEquals("metal 12672", hover.melts());
        assertEquals("metal 12672", hover.costs());
        assertEquals(List.of("minecraft:chains"), hover.tags());
    }

    private static JsonObject familyRow(JsonArray rows, String id) {
        for (JsonElement row : rows) {
            if (id.equals(row.getAsJsonObject().get("id").getAsString())) {
                return row.getAsJsonObject();
            }
        }
        throw new AssertionError(id + " is not in " + rows);
    }

    /**
     * A derived row's source names where its value comes from rather than saying it was derived: up the chain
     * of single-input recipes to the prime ancestor, the hand-keyed item no recipe values, as the ingot made of
     * raw iron alone; and the recipe itself, its grid drawn, where the first recipe mixes inputs, as the chain of
     * nuggets and an ingot.
     */
    @Test
    void derivedRowLinksItsPrimeAncestorOrItsRecipe() throws IOException {
        JsonArray rows = JsonParser.parseString(Valuations.family(shipped(), InventoryView.parse(ironInventory()),
                "minecraft:iron_chain")).getAsJsonObject().getAsJsonArray("rows");
        JsonObject chain = familyRow(rows, "minecraft:iron_chain");
        assertEquals("", chain.get("source").getAsString());
        assertEquals("", chain.get("definedAs").getAsString());
        JsonObject chainFrom = chain.getAsJsonObject("from");
        assertEquals("recipe", chainFrom.get("kind").getAsString());
        assertTrue(chainFrom.getAsJsonArray("grid").toString().contains("minecraft:iron_ingot"), chainFrom.toString());
        JsonObject ingotFrom = familyRow(rows, "minecraft:iron_ingot").getAsJsonObject("from");
        assertEquals("ancestor", ingotFrom.get("kind").getAsString());
        assertTrue(ingotFrom.get("prime").getAsBoolean(), ingotFrom.toString());
        JsonArray ancestors = ingotFrom.getAsJsonArray("chain");
        String prime = ancestors.get(ancestors.size() - 1).getAsString();
        assertEquals(prime, ingotFrom.get("item").getAsString());
        assertTrue(Set.of("minecraft:raw_iron", "minecraft:iron_nugget").contains(prime), ingotFrom.toString());
    }
}
