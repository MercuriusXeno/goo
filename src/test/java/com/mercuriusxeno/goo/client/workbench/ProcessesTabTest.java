package com.mercuriusxeno.goo.client.workbench;

import com.google.gson.JsonElement;
import com.mercuriusxeno.goo.workbench.Processes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the Processes tab's state from a recorded processes reply, with no Screen
 * (decision goo-workbench-opens-the-in-game-manager-screen).
 */
class ProcessesTabTest {

    private static final String MODEL = """
            {"path": "base_values.json", "members": [
              {"key": "_conversions", "keySeparator": null, "kind": "OBJECT", "text": "", "entries": [
                 {"key": "rust", "keySeparator": null, "kind": "VALUE", "text": "\\"metal / 4 -> yore / 9\\"",
                  "entries": [], "leading": null, "trailing": null, "closingGap": null},
                 {"key": "broken", "keySeparator": null, "kind": "VALUE", "text": "\\"metal ->\\"",
                  "entries": [], "leading": null, "trailing": null, "closingGap": null}],
               "leading": null, "trailing": null, "closingGap": null},
              {"key": "_post_conversions", "keySeparator": null, "kind": "OBJECT", "text": "", "entries": [
                 {"key": "#copper", "keySeparator": null, "kind": "VALUE", "text": "\\"@exposed_metal\\"",
                  "entries": [], "leading": null, "trailing": null, "closingGap": null}],
               "leading": null, "trailing": null, "closingGap": null}
            ]}
            """;
    private static final String DESCRIBED = """
            {"sections": [
              {"section": "_conversions", "processes": [
                 {"key": "rust", "kind": "FORMULA", "text": "\\"metal / 4 -> yore / 9\\"", "parsed": true},
                 {"key": "broken", "kind": "FORMULA", "text": "\\"metal ->\\"", "parsed": false,
                  "error": "Formula lacks a right side"}]},
              {"section": "_post_conversions", "processes": [
                 {"key": "#copper", "kind": "ASSIGNMENT", "text": "\\"@exposed_metal\\"", "parsed": true}]}
            ]}
            """;

    private ManagerDraft draft;
    private ProcessesTab tab;

    @BeforeEach
    void readReplies() {
        draft = new ManagerDraft();
        draft.read(MODEL);
        tab = new ProcessesTab(draft);
        tab.readDescribed(DESCRIBED);
    }

    /**
     * Each block lists its entries with their form lower case, their text unquoted and the parser's verdict, and
     * its summary counts the entries and the refused ones.
     */
    @Test
    void sectionsListEntriesWithFormAndVerdict() {
        List<ProcessesTab.ProcessSection> sections = tab.sections();
        assertEquals(List.of("_conversions", "_post_conversions"),
                sections.stream().map(ProcessesTab.ProcessSection::section).toList());
        ProcessesTab.ProcessSection conversions = sections.getFirst();
        assertEquals("2 entries, 1 refused", conversions.summary());
        assertEquals(new ProcessesTab.ProcessRow("rust", "formula", "metal / 4 -> yore / 9", true, "parsed"),
                conversions.rows().get(0));
        assertEquals(new ProcessesTab.ProcessRow("broken", "formula", "metal ->", false,
                "Formula lacks a right side"), conversions.rows().get(1));
        assertEquals("1 entries", sections.get(1).summary());
        assertEquals("assignment", sections.get(1).rows().getFirst().kind());
    }

    /**
     * A delete drops the entry.
     */
    @Test
    void deleteDropsTheDraftsEntry() {
        tab.delete("_conversions", "broken");
        assertTrue(!draft.text().contains("\"broken\""), draft.text());
    }

    /**
     * A Mutators row lays out on two lines, its text on the second at the row's full width, so a formula wider
     * than the old text column (the row less the key, kind, verdict and Delete columns) draws whole
     * (decision mutator-rows-take-an-extra-line).
     */
    @Test
    void rowLaysItsTextOnASecondLineAtFullWidth() {
        int left = 20;
        int right = 420;
        int top = 100;
        int formulaWidth = 320;
        ProcessesTab.RowLayout layout = ProcessesTab.layoutRow(left, right, top);
        assertEquals(2, layout.lines());
        assertEquals(left, layout.textX());
        assertEquals(right - left, layout.textWidth());
        assertTrue(layout.textY() > top, "the text draws below the key line");
        assertTrue(layout.textY() + 10 <= top + layout.height(), "the text line stands inside the row");
        assertTrue(layout.textWidth() >= formulaWidth, "the formula draws whole");
    }

    /**
     * Each form's controls pick its types, prefab, formula or group, and the draft holds the value Processes parses
     * back to the same form (decision expression-builder-composes-every-form-without-typing).
     */
    @Nested
    class Controls {
        private static final String POST = "_post_conversions";
        private static final String FORMS = """
                {"path": "base_values.json", "members": [
                  {"key": "_groups", "keySeparator": null, "kind": "OBJECT", "text": "", "entries": [
                     {"key": "copper", "keySeparator": null, "kind": "VALUE", "text": "[\\"copper_block\\"]",
                      "entries": [], "leading": null, "trailing": null, "closingGap": null}],
                   "leading": null, "trailing": null, "closingGap": null},
                  {"key": "_post_conversions", "keySeparator": null, "kind": "OBJECT", "text": "", "entries": [
                     {"key": "oxidation_metal", "keySeparator": null, "kind": "VALUE",
                      "text": "\\"pulse / 2 -> leaf * 3\\"", "entries": [], "leading": null, "trailing": null,
                      "closingGap": null},
                     {"key": "waxed", "keySeparator": null, "kind": "VALUE", "text": "\\"+$other\\"",
                      "entries": [], "leading": null, "trailing": null, "closingGap": null},
                     {"key": "weathered_metal", "keySeparator": null, "kind": "VALUE", "text": "\\"@waxed\\"",
                      "entries": [], "leading": null, "trailing": null, "closingGap": null},
                     {"key": "#exposed", "keySeparator": null, "kind": "VALUE", "text": "\\"#iron @waxed\\"",
                      "entries": [], "leading": null, "trailing": null, "closingGap": null}],
                   "leading": null, "trailing": null, "closingGap": null}
                ]}
                """;
        private ProcessesTab forms;
        private ManagerDraft formsDraft;

        @BeforeEach
        void readForms() {
            formsDraft = new ManagerDraft();
            formsDraft.read(FORMS);
            forms = new ProcessesTab(formsDraft);
        }

        private String textOf(String key) {
            return formsDraft.sectionEntries(POST).asList().stream().map(JsonElement::getAsJsonObject)
                    .filter(entry -> key.equals(entry.get("key").getAsString())).findFirst().orElseThrow()
                    .get("text").getAsString();
        }

        @Test
        void formulaPicksItsTypesAndNumbers() {
            forms.setFormulaSource(POST, "oxidation_metal", "metal");
            forms.setFormulaDivisor(POST, "oxidation_metal", 4);
            forms.setFormulaTarget(POST, "oxidation_metal", "yore");
            forms.toggleFormulaOperator(POST, "oxidation_metal");
            forms.setFormulaAmount(POST, "oxidation_metal", 9);
            assertEquals("\"metal / 4 -> yore / 9\"", textOf("oxidation_metal"));
            assertEquals(new Processes.Formula("metal", 4, "yore", "/", 9),
                    Processes.parse("oxidation_metal", textOf("oxidation_metal")));
        }

        @Test
        void additivePicksItsPrefab() {
            forms.setAdditive(POST, "waxed", "waxed");
            assertEquals("\"+$waxed\"", textOf("waxed"));
            assertEquals(new Processes.Additive("waxed"), Processes.parse("waxed", textOf("waxed")));
        }

        @Test
        void stackPicksItsFormulaAndCount() {
            assertEquals(List.of("oxidation_metal", "waxed", "weathered_metal"), forms.processNames(POST));
            forms.setStackFormula(POST, "weathered_metal", "oxidation_metal");
            forms.setStackCount(POST, "weathered_metal", 2);
            assertEquals("\"2 @oxidation_metal\"", textOf("weathered_metal"));
            assertEquals(new Processes.Stack(2, "oxidation_metal"),
                    Processes.parse("weathered_metal", textOf("weathered_metal")));
        }

        @Test
        void assignmentPicksItsGroupAndChain() {
            assertEquals(List.of("copper"), forms.groupNames());
            forms.setAssignmentSource(POST, "#exposed", "copper");
            forms.removeAssignmentLink(POST, "#exposed", 0);
            forms.addAssignmentLink(POST, "#exposed", "@exposed_metal");
            assertEquals("\"#copper @exposed_metal\"", textOf("#exposed"));
            assertEquals(new Processes.Assignment("copper", 1, 1, List.of("@exposed_metal")),
                    Processes.parse("#exposed", textOf("#exposed")));
            forms.setAssignmentScale(POST, "#exposed", 3, 4);
            assertEquals("\"#copper * 3 / 4 @exposed_metal\"", textOf("#exposed"));
        }
    }
}
