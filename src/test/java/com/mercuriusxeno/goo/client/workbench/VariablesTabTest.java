package com.mercuriusxeno.goo.client.workbench;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the Variables tab's state from recorded constants evaluate and hover replies, with no Screen
 * (decisions goo-workbench-opens-the-in-game-manager-screen, references-link-to-their-variable-and-hover-a-card).
 */
class VariablesTabTest {

    private static final String MODEL = """
            {"path": "base_values.json", "members": [
              {"key": "_constants", "keySeparator": null, "kind": "OBJECT", "text": "", "entries": [
                 {"key": "base", "keySeparator": null, "kind": "VALUE", "text": "8", "entries": [],
                  "leading": null, "trailing": null, "closingGap": null},
                 {"key": "broken", "keySeparator": null, "kind": "VALUE", "text": "\\"$missing + 1\\"",
                  "entries": [], "leading": null, "trailing": null, "closingGap": null},
                 {"key": "egg", "keySeparator": null, "kind": "VALUE", "text": "{\\"zoo\\": \\"$base\\"}",
                  "entries": [], "leading": null, "trailing": null, "closingGap": null}],
               "leading": null, "trailing": null, "closingGap": null}
            ]}
            """;
    private static final String EVALUATED = """
            {"constants": [
              {"name": "base", "kind": "scalar", "value": "8", "error": null,
               "usedBy": ["minecraft:stick", "oak_log", "plank"], "direct": ["stick"],
               "readers": [{"id": "egg", "kind": "constant"}, {"id": "stick", "kind": "item"}]},
              {"name": "broken", "kind": "scalar", "value": null, "error": "Unknown constant $missing",
               "usedBy": [], "readers": []},
              {"name": "egg", "kind": "tree", "tree": {"zoo": "8"}, "value": null, "error": null,
               "usedBy": ["minecraft:egg"], "readers": [{"id": "minecraft:egg", "kind": "item"}]}
            ]}
            """;
    private static final String HOVER = """
            {"name": "egg", "kind": "tree", "tree": {"zoo": "8", "hex": "1/2"}, "value": null, "error": null,
             "usedBy": ["minecraft:egg"], "direct": [], "readers": [], "expression": "{\\"zoo\\": \\"$base\\"}"}
            """;

    private ManagerDraft draft;
    private VariablesTab tab;

    @BeforeEach
    void readReplies() {
        draft = new ManagerDraft();
        draft.read(MODEL);
        tab = new VariablesTab(draft);
        tab.readEvaluated(EVALUATED);
    }

    private static JsonObject parsed(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    @Nested
    class Blocks {

        /**
         * Each constant reads as a block in file order: its expression unquoted, its kind, what it resolves to,
         * its readers, the items whose own expression names it and, apart from those, the terminus items it
         * cascades into (decision used-by-draws-the-terminus-items-alone).
         */
        @Test
        void blocksReadExpressionResolvedAndReaders() {
            List<VariablesTab.ConstantBlock> blocks = tab.blocks();
            assertEquals(List.of("base", "broken", "egg"), blocks.stream().map(VariablesTab.ConstantBlock::name)
                    .toList());
            VariablesTab.ConstantBlock base = blocks.get(0);
            assertEquals("8", base.expression());
            assertEquals("scalar", base.kind());
            assertEquals("8", base.resolved());
            assertEquals(List.of(new VariablesTab.Reader("egg", "constant"), new VariablesTab.Reader("stick", "item")),
                    base.readers());
            assertEquals(List.of("stick"), base.direct());
            assertEquals(List.of("oak_log", "plank"), base.indirect());
        }

        /**
         * A constant the loader refused carries its error, and its expression shows without JSON quotes.
         */
        @Test
        void erroredConstantCarriesItsError() {
            VariablesTab.ConstantBlock broken = tab.blocks().get(1);
            assertEquals("$missing + 1", broken.expression());
            assertEquals("Unknown constant $missing", broken.error());
        }

        /**
         * A tree constant resolves to each type with its amount.
         */
        @Test
        void treeConstantResolvesPerType() {
            VariablesTab.ConstantBlock egg = tab.blocks().get(2);
            assertEquals("tree", egg.kind());
            assertEquals("zoo 8", egg.resolved());
        }
    }

    @Nested
    class Search {

        private List<String> listedNames() {
            return tab.listed().stream().map(VariablesTab.ConstantBlock::name).toList();
        }

        /**
         * Search text matching one prefab's name lists that prefab alone
         * (decision prefabs-tab-searches-its-list-by-name).
         */
        @Test
        void searchListsThePrefabWhoseNameHoldsTheText() {
            tab.search("rok");
            assertEquals(List.of("broken"), listedNames());
        }

        /**
         * Text in another case and with spaces around it lists the same prefab.
         */
        @Test
        void searchIgnoresCaseAndSurroundingSpaces() {
            tab.search("  BROK ");
            assertEquals(List.of("broken"), listedNames());
        }

        /**
         * Text found only in an expression or a kind lists no prefab: egg's expression holds zoo, broken's holds
         * missing, and egg's kind reads tree.
         */
        @Test
        void searchMatchesTheNameAloneNotTheExpressionOrKind() {
            tab.search("zoo");
            assertEquals(List.of(), listedNames());
            tab.search("missing");
            assertEquals(List.of(), listedNames());
            tab.search("tree");
            assertEquals(List.of(), listedNames());
        }

        /**
         * Blank text lists every prefab in file order.
         */
        @Test
        void blankSearchListsEveryPrefabInFileOrder() {
            tab.search("egg");
            tab.search("   ");
            assertEquals(List.of("base", "broken", "egg"), listedNames());
        }

        /**
         * The search narrows the tab's list alone; the blocks the builder's prefab dropdown reads stay whole.
         */
        @Test
        void searchLeavesTheWholeBlocksForTheDropdowns() {
            tab.search("egg");
            assertEquals(3, tab.blocks().size());
        }
    }

    @Nested
    class Editing {

        /**
         * A click on the read view swaps it to the edit box, and a blur swaps it back.
         */
        @Test
        void clickSwapsToTheEditBoxAndBlurSwapsBack() {
            tab.startEditing("base");
            assertEquals("base", tab.editing().orElseThrow());
            tab.stopEditing();
            assertTrue(tab.editing().isEmpty());
        }

        /**
         * A link opening a variable puts its block into the edit box.
         */
        @Test
        void jumpOpensTheVariablesEditBox() {
            tab.jumpTo("egg");
            assertEquals("egg", tab.editing().orElseThrow());
        }

        /**
         * A pick from the builder's dropdown on a Prefabs expression lands the prefab in the draft by name, none
         * typed (decision expression-builder-composes-every-form-without-typing).
         */
        @Test
        void builderPickOnAPrefabExpressionLandsInTheDraft() {
            SearchDropdown dropdown = SearchDropdown.over(List.of(), tab.blocks().stream()
                    .map(VariablesTab.ConstantBlock::name).toList(), false);
            dropdown.search("bro");
            ExpressionBuilder builder = ExpressionBuilder.of("");
            builder.relate(dropdown.choices().getFirst());
            builder.setScale(0, ExpressionBuilder.Op.DIVIDE, "4");
            tab.edit("base", builder.text());
            assertEquals("$broken / 4", tab.blocks().get(0).expression());
        }

        /**
         * An edit stores an expression as a JSON string in the draft, an integer as written.
         */
        @Test
        void editStoresTheExpressionInTheDraft() {
            tab.edit("base", "$other * 2");
            assertEquals("$other * 2", tab.blocks().get(0).expression());
            assertTrue(draft.text().contains("\\\"$other * 2\\\""));
            tab.edit("base", " 12 ");
            assertEquals("12", tab.blocks().get(0).expression());
        }
    }

    @Nested
    class Requests {

        /**
         * A rename posts its from and to beside the draft's members.
         */
        @Test
        void renamePostsFromToAndMembers() {
            JsonObject request = parsed(tab.renameRequest("base", "standard"));
            assertEquals("base", request.get("from").getAsString());
            assertEquals("standard", request.get("to").getAsString());
            assertEquals(1, request.getAsJsonArray("members").size());
        }

        /**
         * A delete posts the constant's name beside the draft's members.
         */
        @Test
        void deletePostsNameAndMembers() {
            JsonObject request = parsed(tab.deleteRequest("broken"));
            assertEquals("broken", request.get("name").getAsString());
            assertTrue(request.has("members"));
        }

        /**
         * The delete confirm lists every reader the delete removes: the constant's readers, then each removed
         * constant's readers, each once.
         */
        @Test
        void deletionCascadeWalksReadersOfRemovedConstants() {
            assertEquals(List.of(new VariablesTab.Reader("egg", "constant"), new VariablesTab.Reader("stick", "item"),
                    new VariablesTab.Reader("minecraft:egg", "item")), tab.deletionCascade("base"));
            assertTrue(tab.deletionCascade("broken").isEmpty());
        }

        /**
         * The hover card reads the variable's expression, one line per type it resolves to and its terminus items.
         */
        @Test
        void hoverCardReadsExpressionTypesAndTerminus() {
            VariablesTab.HoverCard card = tab.readHover(HOVER);
            assertEquals("egg", card.title());
            assertEquals("{\"zoo\": \"$base\"}", card.expression());
            assertEquals(List.of("zoo 8", "hex 1/2"), card.lines());
            assertEquals(List.of(), card.direct());
            assertEquals(List.of("minecraft:egg"), card.indirect());
        }
    }
}
