package com.mercuriusxeno.goo.client.workbench;

import org.junit.jupiter.api.BeforeEach;
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
     * An edit stores the entry's text as a JSON string in the draft, and a delete drops the entry.
     */
    @Test
    void editAndDeleteChangeTheDraftsEntries() {
        tab.edit("_conversions", "rust", " metal / 2 -> yore / 9 ");
        assertTrue(draft.text().contains("\\\"metal / 2 -> yore / 9\\\""), draft.text());
        tab.delete("_conversions", "broken");
        assertTrue(!draft.text().contains("\"broken\""), draft.text());
    }
}
