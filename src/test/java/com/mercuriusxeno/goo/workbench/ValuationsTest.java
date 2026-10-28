package com.mercuriusxeno.goo.workbench;

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
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    private static BaseValuesModel sample() throws IOException {
        return BaseValuesModel.parse(new String(BaseValuesModelTest.sampleBytes(), StandardCharsets.UTF_8));
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
     * Against an inventory holding every id the sample names and one more, with no recipes: the one more is
     * uncovered, no item the loader values is, and dropping a member from its group uncovers it.
     */
    @Test
    void uncoveredListsWhatNothingCovers() throws IOException {
        BaseValuesModel model = sample();
        List<String> inventory = new ArrayList<>(Valuations.namedIds(model));
        inventory.add("minecraft:not_valued_yet");
        List<String> uncovered = Valuations.uncovered(model, inventory);
        assertTrue(uncovered.contains("minecraft:not_valued_yet"), uncovered.toString());
        Map<Identifier, GooValue> valued = GooBaseValuesPreview.load(model.serialize()).baseValues();
        assertEquals(List.of(), uncovered.stream().filter(id -> valued.containsKey(Identifier.parse(id))
                && !valued.get(Identifier.parse(id)).isEmpty()).toList());

        GroupConvention.Group simpleLogs = GroupConvention.describe(model).stream()
                .filter(group -> "simple_logs".equals(group.name())).findFirst().orElseThrow();
        String dropped = simpleLogs.members().getFirst();
        BaseValuesModel without = GroupConvention.withGroup(model, "simple_logs",
                simpleLogs.members().subList(1, simpleLogs.members().size()));
        assertTrue(Valuations.uncovered(without, inventory).contains(dropped), dropped);
    }

    // Oak log, oak planks and a crafting table: a log makes four planks, and four planks make one table.
    static String logPlankTableInventory() {
        JsonObject root = new JsonObject();
        JsonArray items = new JsonArray();
        for (String path : List.of("oak_log", "oak_planks", "crafting_table")) {
            JsonObject item = new JsonObject();
            item.addProperty("id", "minecraft:" + path);
            item.addProperty("name", path);
            items.add(item);
        }
        root.add("items", items);
        root.add("item_tags", new JsonArray());
        root.add("goo_types", new JsonArray());
        JsonArray recipes = new JsonArray();
        recipes.add(shapedRecipe("minecraft:oak_planks", "minecraft:oak_planks", 4, List.of("L"),
                Map.of("L", "minecraft:oak_log")));
        recipes.add(shapedRecipe("minecraft:crafting_table", "minecraft:crafting_table", 1, List.of("PP", "PP"),
                Map.of("P", "minecraft:oak_planks")));
        root.add("recipes", recipes);
        return root.toString();
    }

    static String plankInventory() {
        JsonObject root = new JsonObject();
        JsonArray items = new JsonArray();
        for (String path : List.of("oak_planks", "stick", "mystery")) {
            JsonObject item = new JsonObject();
            item.addProperty("id", "minecraft:" + path);
            item.addProperty("name", path);
            items.add(item);
        }
        root.add("items", items);
        root.add("item_tags", new JsonArray());
        root.add("goo_types", new JsonArray());
        JsonArray recipes = new JsonArray();
        recipes.add(shapedRecipe("minecraft:stick", "minecraft:stick", 4, List.of("P", "P"),
                Map.of("P", "minecraft:oak_planks")));
        root.add("recipes", recipes);
        return root.toString();
    }

    /**
     * A missing value is an item the Values tab reads as none: a recipe-derived item is never uncovered, and an
     * item nothing values is (decision inventory-hydrates-the-tool-and-writes-no-template).
     */
    @Test
    void uncoveredHoldsOnlyWhatReadsAsNone() {
        BaseValuesModel model = BaseValuesModel.parse("""
                {
                    "oak_planks": {"leaf": 4}
                }
                """);
        assertEquals(List.of("minecraft:mystery"), Valuations.uncovered(model, InventoryView.parse(plankInventory())));
    }

    /**
     * An item no entry names but a recipe values resolves to its derived value, keyed as the Items tab looks it up,
     * so planks solved from valued sticks read their value rather than none
     * (decision derivation-solves-whichever-side-of-a-recipe-is-unknown).
     */
    @Test
    void resolveAnswersARecipeDerivedItem() {
        BaseValuesModel model = BaseValuesModel.parse("""
                {
                    "stick": {"leaf": 2}
                }
                """);
        JsonArray entries = JsonParser.parseString(Valuations.resolved(model, InventoryView.parse(plankInventory())))
                .getAsJsonObject().getAsJsonArray("entries");
        assertEquals("4", entryNamed(entries, "oak_planks").getAsJsonObject("value").get("leaf").getAsString());
        assertFalse(entries.asList().stream().anyMatch(entry -> "mystery".equals(entry.getAsJsonObject().get("key")
                .getAsString())));
    }

    /**
     * An entry reading {} values nothing, so it reads as none with nothing defined and stands uncovered
     * (decision inventory-hydrates-the-tool-and-writes-no-template).
     */
    @Test
    void emptyEntryReadsAsNone() throws IOException {
        BaseValuesModel model = BaseValuesModel.parse("""
                {
                    "oak_planks": {"leaf": 4},
                    "mystery": {}
                }
                """);
        InventoryView inventory = InventoryView.parse(plankInventory());
        JsonObject row = familyRow(JsonParser.parseString(Valuations.family(model, inventory, "minecraft:mystery"))
                .getAsJsonObject().getAsJsonArray("rows"), "minecraft:mystery");
        assertEquals("none", row.get("source").getAsString());
        assertEquals("", row.get("definedAs").getAsString());
        assertTrue(Valuations.uncovered(model, inventory).contains("minecraft:mystery"));
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
        for (String path : List.of("iron_chain", "iron_nugget", "iron_ingot", "raw_iron", "raw_iron_block")) {
            JsonObject item = new JsonObject();
            item.addProperty("id", "minecraft:" + path);
            item.addProperty("name", path);
            items.add(item);
        }
        root.add("items", items);
        JsonArray tags = new JsonArray();
        JsonObject chains = new JsonObject();
        chains.addProperty("id", "minecraft:chains");
        JsonArray members = new JsonArray();
        members.add("minecraft:iron_chain");
        chains.add("members", members);
        tags.add(chains);
        root.add("item_tags", tags);
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
        // A recipe making a hand-keyed item: the walk to the prime ancestor stops at the hand-keyed raw iron.
        recipes.add(plainRecipe("minecraft:raw_iron", "minecraft:crafting", "minecraft:raw_iron", 9,
                "minecraft:raw_iron_block", 1));
        recipes.add(plainRecipe("minecraft:raw_iron_block", "minecraft:crafting", "minecraft:raw_iron_block", 1,
                "minecraft:raw_iron", 9));
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
        Valuations.Hover hover = Valuations.hover(sample(), InventoryView.parse(ironInventory()),
                "minecraft:iron_chain");
        assertEquals(java.util.Arrays.asList(null, "minecraft:iron_nugget", null, null, "minecraft:iron_ingot", null,
                null, "minecraft:iron_nugget", null), hover.grid());
        assertEquals(1, hover.count());
        assertTrue(hover.ratios().contains("1 iron_chain = 11 iron_nugget"), hover.ratios().toString());
        assertTrue(hover.ratios().contains("1 iron_chain = 11/9 iron_ingot"), hover.ratios().toString());
        assertEquals("metal 12672", hover.value());
        assertEquals(List.of("minecraft:chains"), hover.tags());
    }

    /**
     * The hover and every Values row answer the value alone, with no floor or ceiling rounding beside it
     * (decision values-tab-drops-melts-and-costs).
     */
    @Test
    void hoverAndRowsCarryNoMeltsOrCosts() throws IOException {
        InventoryView inventory = InventoryView.parse(ironInventory());
        JsonObject hover = JsonParser.parseString(ValuesJson.hover(sample(), inventory, "minecraft:iron_chain"))
                .getAsJsonObject();
        assertEquals("metal 12672", hover.get("value").getAsString());
        assertFalse(hover.has("melts") || hover.has("costs"), hover.toString());
        JsonArray rows = JsonParser.parseString(Valuations.family(sample(), inventory, "minecraft:iron_chain"))
                .getAsJsonObject().getAsJsonArray("rows");
        for (JsonElement row : rows) {
            assertFalse(row.getAsJsonObject().has("melts") || row.getAsJsonObject().has("costs"), row.toString());
        }
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
     * raw iron alone, stopping there though a recipe makes raw iron too; and the recipe itself, its grid drawn, where the first recipe mixes inputs, as the chain of
     * nuggets and an ingot.
     */
    @Test
    void derivedRowLinksItsPrimeAncestorOrItsRecipe() throws IOException {
        JsonArray rows = JsonParser.parseString(Valuations.family(sample(), InventoryView.parse(ironInventory()),
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
        assertEquals("minecraft:raw_iron", prime, ingotFrom.toString());
    }

    private static final String GLASS_ROOTED = """
            {
                "_roots": ["glass"],
                "glass": {"rock": 8}
            }
            """;

    private static String glassInventory() {
        JsonObject root = new JsonObject();
        JsonArray items = new JsonArray();
        for (String path : List.of("glass", "glass_pane", "glass_bottle")) {
            JsonObject item = new JsonObject();
            item.addProperty("id", "minecraft:" + path);
            item.addProperty("name", path);
            items.add(item);
        }
        root.add("items", items);
        root.add("item_tags", new JsonArray());
        root.add("goo_types", new JsonArray());
        JsonArray recipes = new JsonArray();
        recipes.add(plainRecipe("minecraft:glass_pane", "minecraft:crafting", "minecraft:glass_pane", 16,
                "minecraft:glass", 6));
        recipes.add(plainRecipe("minecraft:glass_bottle", "minecraft:crafting", "minecraft:glass_bottle", 3,
                "minecraft:glass", 3));
        root.add("recipes", recipes);
        return root.toString();
    }

    private static JsonObject glassFamily(String id) throws IOException {
        return JsonParser.parseString(Valuations.family(BaseValuesModel.parse(GLASS_ROOTED),
                InventoryView.parse(glassInventory()), id)).getAsJsonObject();
    }

    /**
     * The family reads its root, the count of items the root solves and the smallest remainder, whether the root
     * itself is selected or an item it solves; each row reads its recipe's remainder at the root as a mixed number,
     * non-dividing rows first (decision workbench-shows-each-roots-remainders-and-items-solved).
     */
    @Test
    void familyReadsTheRootItsCountAndRemaindersInOrder() throws IOException {
        for (String selected : List.of("minecraft:glass", "minecraft:glass_pane")) {
            JsonObject family = glassFamily(selected);
            JsonObject root = family.getAsJsonObject("root");
            assertEquals("minecraft:glass", root.get("id").getAsString());
            assertEquals(2, root.get("solved").getAsInt());
            assertEquals("³⁄₈", root.get("smallestRemainder").getAsString());
        }
        JsonArray rows = glassFamily("minecraft:glass").getAsJsonArray("rows");
        assertEquals(List.of("minecraft:glass_pane", "minecraft:glass_bottle", "minecraft:glass"),
                rows.asList().stream().map(row -> row.getAsJsonObject().get("id").getAsString()).toList());
        assertEquals("³⁄₈", familyRow(rows, "minecraft:glass_pane").get("remainder").getAsString());
        assertEquals("0", familyRow(rows, "minecraft:glass_bottle").get("remainder").getAsString());
    }

    /**
     * The family's remainder for a recipe is the audit section's for the same recipe at the same root, since both
     * read GooRootRemainders.
     */
    @Test
    void auditAndFamilyAgreeOnEachRemainder() throws IOException {
        JsonArray rows = glassFamily("minecraft:glass").getAsJsonArray("rows");
        com.mercuriusxeno.goo.command.GooAuditSection section = new com.mercuriusxeno.goo.command
                .RootRemaindersAuditRule().run(new com.mercuriusxeno.goo.command.GooAuditInputs(
                GooBaseValuesPreview.derivedRegistry(GLASS_ROOTED, InventoryView.parse(glassInventory()).recipeInputs()),
                List.of(), Set.of()));
        for (String output : List.of("glass_pane", "glass_bottle")) {
            String exact = familyRow(rows, "minecraft:" + output).get("remainderExact").getAsString();
            assertTrue(section.lines().stream().anyMatch(line -> line.contains(" " + output + ":")
                    && line.endsWith("remainder " + exact)), output + " " + exact + " " + section.lines());
        }
    }
}
