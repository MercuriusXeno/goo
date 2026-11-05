package com.mercuriusxeno.goo.client.workbench;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the one search rule every tab box and dropdown matches by (decision one-search-rule-serves-every-box-and-dropdown).
 */
class SearchTest {
    /**
     * A query trims and folds case; a leading $ narrows to prefabs and a leading # to groups and tags, the mark
     * stripped before the name matches; a blank query matches every candidate.
     */
    @Test
    void matchesTrimmedFoldedAndMarkedQueries() {
        assertTrue(Search.matches(Search.query("  IR "), Search.Kind.ITEM, "Iron Ingot"));
        assertFalse(Search.matches(Search.query("ir"), Search.Kind.ITEM, "Wax"));
        assertTrue(Search.matches(Search.query("$ir"), Search.Kind.PREFAB, "iron_block"));
        assertFalse(Search.matches(Search.query("$ir"), Search.Kind.GROUP, "iron"));
        assertTrue(Search.matches(Search.query("#PL"), Search.Kind.GROUP, "planks"));
        assertFalse(Search.matches(Search.query("#pl"), Search.Kind.PREFAB, "planks"));
        assertTrue(Search.matches(Search.query(" "), Search.Kind.ITEM, "anything"));
        assertTrue(Search.matches(Search.query("oak"), Search.Kind.ITEM, "Stick", "minecraft:oak_stick"));
    }
}
