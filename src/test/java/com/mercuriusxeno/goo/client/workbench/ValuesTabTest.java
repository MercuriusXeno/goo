package com.mercuriusxeno.goo.client.workbench;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the Values tab's state from recorded operation replies, with no Screen
 * (decision goo-workbench-opens-the-in-game-manager-screen).
 */
class ValuesTabTest {

    private static final String INVENTORY = """
            {"items": [{"id": "minecraft:stick", "name": "Stick"}, {"id": "minecraft:torch", "name": "Torch"},
                       {"id": "goo:gasket", "name": "Choral Gasket"}],
             "item_tags": [{"id": "minecraft:logs", "members": ["minecraft:oak_log", "minecraft:birch_log"]}],
             "goo_types": [], "recipes": []}
            """;
    private static final String MODEL = """
            {"path": "base_values.json", "members": [
              {"key": "stick", "keySeparator": null, "kind": "VALUE", "text": "\\"$base / 4\\"", "entries": [],
               "leading": null, "trailing": null, "closingGap": null},
              {"key": "_restricted", "keySeparator": null, "kind": "ARRAY", "text": "", "entries": [],
               "leading": null, "trailing": null, "closingGap": null}
            ]}
            """;
    private static final String RESOLVED = """
            {"entries": [{"key": "stick", "value": {"leaf": "48", "metal": "3/2"}, "denied": false,
                          "restricted": false, "error": null}]}
            """;

    private ValuesTab tab;

    @BeforeEach
    void readReplies() {
        tab = new ValuesTab();
        tab.readInventory(INVENTORY);
        tab.readModel(MODEL);
        tab.readResolved(RESOLVED);
    }

    private static List<String> ids(List<ValuesTab.ManagerEntry> entries) {
        return entries.stream().map(ValuesTab.ManagerEntry::id).toList();
    }

    @Nested
    class Listing {

        /**
         * A blank search lists every item under the Items filter, and every item tag after # under the Tags
         * filter, the tag named by its member count.
         */
        @Test
        void blankSearchListsItemsAndTagsUnderTheirFilters() {
            assertEquals(List.of("minecraft:stick", "minecraft:torch", "goo:gasket"), ids(tab.listed()));
            tab.filter(ValuesTab.Filter.TAGS);
            List<ValuesTab.ManagerEntry> tags = tab.listed();
            assertEquals(List.of("#minecraft:logs"), ids(tags));
            assertEquals("2 members", tags.getFirst().name());
            assertTrue(tags.getFirst().tag());
        }

        /**
         * The search keeps entries whose id or name holds the text, ignoring case.
         */
        @Test
        void searchFiltersByIdOrNameIgnoringCase() {
            tab.search("  CHORAL ");
            assertEquals(List.of("goo:gasket"), ids(tab.listed()));
            tab.search("minecraft:");
            assertEquals(List.of("minecraft:stick", "minecraft:torch"), ids(tab.listed()));
        }
    }

    @Nested
    class SelectedRow {

        /**
         * The selected entry's row reads its expression unquoted from the model and each type's amount from the
         * resolve reply, keyed by the bare id the file spells.
         */
        @Test
        void rowReadsExpressionAndResolvedAmounts() {
            tab.select("minecraft:stick");
            ValuesTab.EntryRow row = tab.selectedRow().orElseThrow();
            assertEquals("Stick", row.name());
            assertEquals("$base / 4", row.expression());
            assertEquals(Map.of("leaf", "48", "metal", "3/2"), row.amounts());
            assertEquals("", row.error());
        }

        /**
         * An entry the file holds no line for reads an empty expression and no amounts.
         */
        @Test
        void entryOutsideTheFileReadsEmpty() {
            tab.select("minecraft:torch");
            ValuesTab.EntryRow row = tab.selectedRow().orElseThrow();
            assertEquals("", row.expression());
            assertTrue(row.amounts().isEmpty());
        }
    }

    @Nested
    class MissingValues {

        private static final String GROUPED_MODEL = """
                {"path": "base_values.json", "members": [
                  {"key": "_groups", "keySeparator": null, "kind": "OBJECT", "text": "", "entries": [
                     {"key": "lights", "keySeparator": null, "kind": "VALUE", "text": "[\\"torch\\"]",
                      "entries": [], "leading": null, "trailing": null, "closingGap": null}],
                   "leading": null, "trailing": null, "closingGap": null},
                  {"key": "stick", "keySeparator": null, "kind": "VALUE", "text": "\\"$base / 4\\"", "entries": [],
                   "leading": null, "trailing": null, "closingGap": null},
                  {"key": "#lights", "keySeparator": null, "kind": "VALUE", "text": "{\\"glow\\": 4}", "entries": [],
                   "leading": null, "trailing": null, "closingGap": null},
                  {"key": "goo:gasket", "keySeparator": null, "kind": "VALUE", "text": "\\"denied\\"", "entries": [],
                   "leading": null, "trailing": null, "closingGap": null},
                  {"key": "_restricted", "keySeparator": null, "kind": "ARRAY", "text": "", "entries": [
                     {"key": null, "keySeparator": null, "kind": "VALUE", "text": "\\"stick\\"", "entries": [],
                      "leading": null, "trailing": null, "closingGap": null}],
                   "leading": null, "trailing": null, "closingGap": null}
                ]}
                """;

        /**
         * Each item names where its value comes from, as the page's source badge does: its own entry, a #group
         * entry over a _groups group, a denial, or nothing in the file, with a restriction appended.
         */
        @Test
        void eachItemNamesWhereItsValueComesFrom() {
            tab.readModel(GROUPED_MODEL);
            assertEquals(new ValuesTab.Source("entry, restricted", ValuesTab.SourceKind.ENTRY),
                    tab.sourceOf("minecraft:stick"));
            assertEquals(new ValuesTab.Source("group #lights", ValuesTab.SourceKind.GROUP),
                    tab.sourceOf("minecraft:torch"));
            assertEquals(new ValuesTab.Source("denied", ValuesTab.SourceKind.DENIED), tab.sourceOf("goo:gasket"));
            tab.readInventory("""
                    {"items": [{"id": "minecraft:dirt", "name": "Dirt"}], "item_tags": []}
                    """);
            assertEquals(new ValuesTab.Source("not in the file", ValuesTab.SourceKind.NONE),
                    tab.sourceOf("minecraft:dirt"));
        }

        /**
         * The Uncovered filter lists the items the uncovered reply names, its chip counts them, and the Tags filter
         * lists the tags alone.
         */
        @Test
        void uncoveredFilterListsTheItemsWithNoValue() {
            tab.readUncovered("{\"items\": [\"minecraft:torch\"]}");
            assertEquals(1, tab.uncoveredCount());
            tab.filter(ValuesTab.Filter.UNCOVERED);
            assertEquals(List.of("minecraft:torch"), ids(tab.listed()));
            tab.filter(ValuesTab.Filter.TAGS);
            assertEquals(List.of("#minecraft:logs"), ids(tab.listed()));
            tab.filter(ValuesTab.Filter.ITEMS);
            assertEquals(List.of("minecraft:stick", "minecraft:torch", "goo:gasket"), ids(tab.listed()));
        }
    }

    @Nested
    class Save {

        private JsonObject memberKeyed(String key) {
            return JsonParser.parseString(tab.modelText()).getAsJsonObject().getAsJsonArray("members").asList()
                    .stream().map(member -> member.getAsJsonObject())
                    .filter(member -> key.equals(member.get("key").getAsString())).findFirst().orElseThrow();
        }

        /**
         * An edit to a standing entry rewrites its text, an expression quoted as a JSON string, in the text a save
         * posts.
         */
        @Test
        void editOfAStandingEntryCarriesIntoTheSaveText() {
            tab.select("minecraft:stick");
            tab.edit("$base / 2");
            assertEquals("\"$base / 2\"", memberKeyed("stick").get("text").getAsString());
        }

        /**
         * An edit to an entry the file lacks adds it by its bare key above the closing sections, an integer stored as
         * written.
         */
        @Test
        void editOfANewEntryAddsItAboveTheClosingSections() {
            tab.select("minecraft:torch");
            tab.edit(" 12 ");
            List<String> keys = JsonParser.parseString(tab.modelText()).getAsJsonObject().getAsJsonArray("members")
                    .asList().stream().map(member -> member.getAsJsonObject().get("key").getAsString()).toList();
            assertEquals(List.of("stick", "torch", "_restricted"), keys);
            assertEquals("12", memberKeyed("torch").get("text").getAsString());
        }

        /**
         * A pick from the builder's dropdown on an Items value lands the item in the draft by id, none typed
         * (decision expression-builder-composes-every-form-without-typing).
         */
        @Test
        void builderPickOnAnItemsValueLandsInTheDraft() {
            tab.select("minecraft:torch");
            SearchDropdown dropdown = SearchDropdown.over(tab.entries(), List.of(), true);
            dropdown.search("stick");
            ExpressionBuilder builder = ExpressionBuilder.of("");
            builder.relate(dropdown.choices().getFirst());
            builder.setCount(0, "2");
            tab.edit(builder.text());
            assertEquals("\"2 stick\"", memberKeyed("torch").get("text").getAsString());
        }
    }
}
