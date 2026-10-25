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
         * A blank search lists every item, then every item tag after #, the tag named by its member count.
         */
        @Test
        void blankSearchListsItemsThenTags() {
            List<ValuesTab.ManagerEntry> listed = tab.listed();
            assertEquals(List.of("minecraft:stick", "minecraft:torch", "goo:gasket", "#minecraft:logs"), ids(listed));
            assertEquals("2 members", listed.getLast().name());
            assertTrue(listed.getLast().tag());
        }

        /**
         * The search keeps entries whose id or name holds the text, ignoring case.
         */
        @Test
        void searchFiltersByIdOrNameIgnoringCase() {
            tab.search("  CHORAL ");
            assertEquals(List.of("goo:gasket"), ids(tab.listed()));
            tab.search("minecraft:");
            assertEquals(List.of("minecraft:stick", "minecraft:torch", "#minecraft:logs"), ids(tab.listed()));
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
    }
}
