package com.mercuriusxeno.goo.client.workbench;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the marks each tab holds for the next save to remove, with no Screen (decision
 * every-delete-marks-for-removal-and-a-gold-arrow-restores).
 */
class RemovalMarksTest {

    /**
     * Marks list in the order they were made, each once, apart per tab; a restore drops one and a Revert's clear
     * drops every tab's.
     */
    @Test
    void marksKeepTheirOrderPerTabUntilRestoredOrCleared() {
        RemovalMarks marks = new RemovalMarks();
        marks.mark(RemovalMarks.Tab.PREFABS, "wood");
        marks.mark(RemovalMarks.Tab.PREFABS, "plank");
        marks.mark(RemovalMarks.Tab.PREFABS, "wood");
        marks.mark(RemovalMarks.Tab.GROUPS, "logs");
        assertEquals(List.of("wood", "plank"), marks.marked(RemovalMarks.Tab.PREFABS));
        assertFalse(marks.isMarked(RemovalMarks.Tab.GROUPS, "wood"));
        marks.restore(RemovalMarks.Tab.PREFABS, "wood");
        assertEquals(List.of("plank"), marks.marked(RemovalMarks.Tab.PREFABS));
        assertTrue(marks.isMarked(RemovalMarks.Tab.GROUPS, "logs"));
        marks.clear();
        assertEquals(List.of(), marks.marked(RemovalMarks.Tab.GROUPS));
        assertEquals(List.of(), marks.marked(RemovalMarks.Tab.PREFABS));
    }
}
