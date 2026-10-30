package com.mercuriusxeno.goo.client.workbench;

import com.google.gson.JsonElement;
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

    private static final ManagerEntrySource ENTRIES = new ListedEntrySource(
            List.of(new ManagerEntrySource.ItemName("minecraft:stick", "Stick"),
                    new ManagerEntrySource.ItemName("minecraft:torch", "Torch"),
                    new ManagerEntrySource.ItemName("goo:gasket", "Choral Gasket")),
            List.of(new ManagerEntrySource.TagSize("minecraft:logs", 2)));
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

    private ManagerDraft draft;
    private ValuesTab tab;

    @BeforeEach
    void readReplies() {
        draft = new ManagerDraft();
        tab = new ValuesTab(draft);
        tab.readEntries(ENTRIES);
        tab.readModel(MODEL);
        tab.readResolved(RESOLVED);
    }

    private static List<String> ids(List<ValuesTab.ManagerEntry> entries) {
        return entries.stream().map(ValuesTab.ManagerEntry::id).toList();
    }

    @Nested
    class Listing {

        /**
         * The entries read from the client's source run each item by id and name, then each item tag as #id named
         * by its member count (decision client-builds-items-and-tags-from-its-own-registries).
         */
        @Test
        void entriesReadItemsThenTagsFromTheClientSource() {
            ValuesTab fresh = new ValuesTab();
            fresh.readEntries(new ListedEntrySource(
                    List.of(new ManagerEntrySource.ItemName("minecraft:apple", "Apple"),
                            new ManagerEntrySource.ItemName("minecraft:bread", "Bread")),
                    List.of(new ManagerEntrySource.TagSize("minecraft:planks", 3))));
            assertEquals(List.of(new ValuesTab.ManagerEntry("minecraft:apple", "Apple", false),
                    new ValuesTab.ManagerEntry("minecraft:bread", "Bread", false),
                    new ValuesTab.ManagerEntry("#minecraft:planks", "3 members", true)), fresh.entries());
        }

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

        /**
         * The Fractional filter lists the items where any one goo type's amount has a part under 1, leaving out
         * items whose every amount is whole and every tag (decision items-filter-set-takes-a-fractional-chip).
         */
        @Test
        void fractionalFilterListsTheItemsWithAPartUnderOne() {
            tab.readResolved("""
                    {"entries": [{"key": "stick", "value": {"leaf": "48", "metal": "3/2"}, "denied": false},
                                 {"key": "torch", "value": {"glow": "4", "leaf": "12"}, "denied": false}]}
                    """);
            tab.filter(ValuesTab.Filter.FRACTIONAL);
            assertEquals(List.of("minecraft:stick"), ids(tab.listed()));
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
         * Removing the selected item's entry drops its line from the draft and keeps every other member, so the
         * item reads as the file never naming it.
         */
        @Test
        void removingTheEntryDropsItFromTheDraft() {
            tab.select("minecraft:stick");
            assertTrue(tab.selectedHasEntry());
            tab.removeEntry();
            JsonObject posted = JsonParser.parseString(tab.postedText()).getAsJsonObject();
            assertEquals(List.of(), posted.getAsJsonArray("changed").asList());
            assertEquals(List.of("stick"), posted.getAsJsonArray("removed").asList().stream()
                    .map(JsonElement::getAsString).toList());
            assertEquals("", tab.selectedRow().orElseThrow().expression());
            assertTrue(!tab.selectedHasEntry());
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

        /**
         * Opening an item from a used-by icon lists every item, selects it and answers its place in the list, so the
         * pane scrolls it into view (decision used-by-icons-link-to-items-and-hover-their-value).
         */
        @Test
        void openingAnItemSelectsItUnderTheItemsFilter() {
            tab.filter(ValuesTab.Filter.TAGS);
            tab.search("logs");
            assertEquals(2, tab.open("goo:gasket"));
            assertEquals(ValuesTab.Filter.ITEMS, tab.filter());
            assertEquals("goo:gasket", tab.selectedRow().orElseThrow().id());
        }

        /**
         * An item's amounts read from the resolve reply by its namespaced id, empty for an item nothing values.
         */
        @Test
        void worthOfReadsAnItemsResolvedValue() {
            assertEquals(Map.of("leaf", "48", "metal", "3/2"), tab.worthOf("minecraft:stick"));
            assertTrue(tab.worthOf("minecraft:torch").isEmpty());
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
         * Each item names where its value comes from: Valued for its own entry, the group's bare name for a #group
         * entry over a _groups group, a denial, derived where recipes value an entryless item, and "no value" where
         * nothing does, with its restriction beside; "no value" alone reads red (decisions
         * items-rows-read-valued-derived-or-no-value-with-values-inline,
         * items-row-second-line-is-the-equality-expression).
         */
        @Test
        void eachItemNamesWhereItsValueComesFrom() {
            tab.readModel(GROUPED_MODEL);
            tab.readResolved("""
                    {"entries": [{"key": "oak_planks", "value": {"leaf": "4"}, "denied": false,
                                  "restricted": false, "error": null}]}
                    """);
            tab.readDerivedFrom("""
                    {"items": {"minecraft:oak_planks":
                        {"inputs": [{"id": "minecraft:oak_log", "count": 1}], "output": 4}}}
                    """);
            ValuesTab.Source valued = tab.sourceOf("minecraft:stick");
            assertEquals(new ValuesTab.Source("Valued", ValuesTab.SourceKind.ENTRY, true), valued);
            ValuesTab.Source group = tab.sourceOf("minecraft:torch");
            assertEquals(new ValuesTab.Source("lights", ValuesTab.SourceKind.GROUP, false), group);
            ValuesTab.Source denied = tab.sourceOf("goo:gasket");
            assertEquals(new ValuesTab.Source("denied", ValuesTab.SourceKind.DENIED, false), denied);
            ValuesTab.Source derived = tab.sourceOf("minecraft:oak_planks");
            assertEquals(new ValuesTab.Source("derived", ValuesTab.SourceKind.DERIVED, false), derived);
            assertEquals(new ValuesTab.Recipe(List.of(new ValuesTab.Input("minecraft:oak_log", 1)), 4),
                    tab.derivedFrom("minecraft:oak_planks"));
            ValuesTab.Source none = tab.sourceOf("minecraft:dirt");
            assertEquals(new ValuesTab.Source("no value", ValuesTab.SourceKind.NONE, false), none);
            List<ValuesTab.Source> notRed = List.of(valued, group, denied, derived);
            assertEquals(ValuesPane.RED, ValuesPane.colorOf(none.kind()));
            assertTrue(notRed.stream().noneMatch(source -> ValuesPane.colorOf(source.kind()) == ValuesPane.RED));
        }

        private static final String POST_CONVERTED_MODEL = """
                {"path": "base_values.json", "members": [
                  {"key": "_groups", "keySeparator": null, "kind": "OBJECT", "text": "", "entries": [
                     {"key": "concrete_powder", "keySeparator": null, "kind": "VALUE",
                      "text": "[\\"brown_concrete_powder\\"]", "entries": [], "leading": null, "trailing": null,
                      "closingGap": null},
                     {"key": "concrete", "keySeparator": null, "kind": "VALUE", "text": "[\\"brown_concrete\\"]",
                      "entries": [], "leading": null, "trailing": null, "closingGap": null}],
                   "leading": null, "trailing": null, "closingGap": null},
                  {"key": "#concrete_powder", "keySeparator": null, "kind": "VALUE", "text": "\\"$base\\"",
                   "entries": [], "leading": null, "trailing": null, "closingGap": null},
                  {"key": "_post_conversions", "keySeparator": null, "kind": "OBJECT", "text": "", "entries": [
                     {"key": "#concrete", "keySeparator": null, "kind": "VALUE", "text": "\\"#concrete_powder\\"",
                      "entries": [], "leading": null, "trailing": null, "closingGap": null}],
                   "leading": null, "trailing": null, "closingGap": null}
                ]}
                """;

        /**
         * Brown concrete, which the 26.1 file values through the #concrete group's post-conversion and no recipe
         * input, reads the group that values it rather than a bare "derived from" (decision
         * diagnose-and-correct-group-derived-reading-derived-from).
         */
        @Test
        void postConvertedGroupMemberReadsTheGroupThatValuesIt() {
            tab.readModel(POST_CONVERTED_MODEL);
            tab.readResolved("""
                    {"entries": [{"key": "brown_concrete", "value": {"rock": "192"}, "denied": false,
                                  "restricted": false, "error": null}]}
                    """);
            tab.readDerivedFrom("{\"items\": {}}");
            assertEquals(new ValuesTab.Source("concrete", ValuesTab.SourceKind.GROUP, false),
                    tab.sourceOf("minecraft:brown_concrete"));
        }

        /**
         * An item valued with no entry, no group and no recipe inputs to name reads derived, the same as one with
         * inputs, so no row reads a bare "derived from" (decisions
         * diagnose-and-correct-group-derived-reading-derived-from, items-row-second-line-is-the-equality-expression).
         */
        @Test
        void valuedItemWithNoInputsNeverReadsABareDerivedFrom() {
            tab.readResolved("""
                    {"entries": [{"key": "torch", "value": {"glow": "4"}, "denied": false,
                                  "restricted": false, "error": null}]}
                    """);
            tab.readDerivedFrom("{\"items\": {}}");
            assertEquals(new ValuesTab.Source("derived", ValuesTab.SourceKind.DERIVED, false),
                    tab.sourceOf("minecraft:torch"));
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
            return JsonParser.parseString(draft.text()).getAsJsonObject().getAsJsonArray("members").asList()
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
            List<String> keys = JsonParser.parseString(draft.text()).getAsJsonObject().getAsJsonArray("members")
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
