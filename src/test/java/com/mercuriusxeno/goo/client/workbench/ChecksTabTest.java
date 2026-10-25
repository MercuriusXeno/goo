package com.mercuriusxeno.goo.client.workbench;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the Checks tab's state from the checks a recorded model reply carries, with no Screen (decisions
 * goo-workbench-opens-the-in-game-manager-screen, type-source-checks-are-rules-that-gate-promote).
 */
class ChecksTabTest {

    private static final String MODEL = """
            {"path": "base_values.json", "members": [], "checks": [
              {"title": "every goo type has a source", "passed": false,
               "wants": [{"item": "infinity", "lacks": "no item grants it"},
                         {"item": "xeno", "lacks": "no item grants it"}]},
              {"title": "each source is an obtainable item", "passed": true, "wants": []},
              {"title": "no type is sourced from creative-only items", "passed": false,
               "wants": [{"item": "minecraft:bedrock", "lacks": "creative only"}]}
            ]}
            """;

    /**
     * The badge counts the failing rules, and each rule reads pass or FAIL with a failing rule's wants beneath.
     */
    @Test
    void badgeCountsFailingRulesAndRowsListTheirWants() {
        ManagerDraft draft = new ManagerDraft();
        draft.read(MODEL);
        ChecksTab tab = new ChecksTab(draft);
        assertEquals("Checks (2 red)", tab.badge("Checks"));
        List<ChecksTab.CheckRow> rows = tab.rows();
        assertEquals(new ChecksTab.CheckRow("FAIL  every goo type has a source", false,
                List.of("infinity: no item grants it", "xeno: no item grants it")), rows.get(0));
        assertEquals(new ChecksTab.CheckRow("pass  each source is an obtainable item", true, List.of()), rows.get(1));
        assertEquals(List.of("minecraft:bedrock: creative only"), rows.get(2).wants());
    }

    /**
     * A model with every rule passing, or none checked yet, leaves the badge bare.
     */
    @Test
    void noFailingRuleLeavesTheBadgeBare() {
        ManagerDraft draft = new ManagerDraft();
        draft.read("{\"members\": []}");
        assertEquals("Checks", new ChecksTab(draft).badge("Checks"));
    }
}
