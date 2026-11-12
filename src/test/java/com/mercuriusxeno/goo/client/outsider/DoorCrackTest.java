package com.mercuriusxeno.goo.client.outsider;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.function.ToDoubleFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the conjured door's outline: a jagged rip pointed at head and foot,
 * each side its own uneven zigzag, standing the doorway's full height at
 * every size and widening in its own shape to the one wide, two tall doorway
 * (decision conjured-door-opens-as-a-jagged-rip).
 */
class DoorCrackTest {

    private static final double FULL_WIDE = 1.0;
    private static final double FULL_TALL = 2.0;
    private static final double EXACT = 1.0E-9;

    @Test
    void aFullRipSpansTheDoorwayAndPointsOnTheCenterlineAtHeadAndFoot() {
        List<RipRow> outline = DoorCrack.outline(1);

        assertEquals(FULL_WIDE, width(outline), EXACT, "wide");
        assertEquals(FULL_TALL, height(outline), EXACT, "tall");
        assertPointedAt(outline.getFirst(), 0, "foot");
        assertPointedAt(outline.getLast(), FULL_TALL, "head");
    }

    @Test
    void eachSideZigzagsUnevenlyAndTheSidesDiffer() {
        List<RipRow> outline = DoorCrack.outline(1);

        assertTrue(turnsOf(outline, RipRow::leftReach) >= 2, "the left side turns back on itself twice");
        assertTrue(turnsOf(outline, RipRow::rightReach) >= 2, "the right side turns back on itself twice");
        assertTrue(outline.stream().anyMatch(row -> Math.abs(row.leftReach() - row.rightReach()) > EXACT),
                "at some height the left reach and the right reach differ");
    }

    @Test
    void atEverySizeTheRipHoldsItsShapeAndNeverNarrows() {
        double was = 0;
        for (int step = 1; step <= 20; step++) {
            double size = step / 20.0;
            List<RipRow> outline = DoorCrack.outline(size);

            assertEquals(FULL_TALL, height(outline), EXACT, "the full height at size " + size);
            assertPointedAt(outline.getFirst(), 0, "foot at size " + size);
            assertPointedAt(outline.getLast(), FULL_TALL, "head at size " + size);
            assertTrue(turnsOf(outline, RipRow::leftReach) >= 2, "the left side zigzags at size " + size);
            assertTrue(turnsOf(outline, RipRow::rightReach) >= 2, "the right side zigzags at size " + size);
            double wide = width(outline);
            assertTrue(wide >= was, "at size " + size + " the width " + wide + " fell below " + was);
            was = wide;
        }
    }

    private static void assertPointedAt(RipRow row, double up, String end) {
        assertEquals(up, row.up(), EXACT, end + " height");
        assertEquals(0, row.leftReach(), EXACT, end + " left reach meets the centerline");
        assertEquals(0, row.rightReach(), EXACT, end + " right reach meets the centerline");
    }

    /** How many times a side's reach changes direction from foot to head. */
    private static int turnsOf(List<RipRow> outline, ToDoubleFunction<RipRow> reach) {
        int turns = 0;
        double lastStep = 0;
        for (int index = 1; index < outline.size(); index++) {
            double step = reach.applyAsDouble(outline.get(index)) - reach.applyAsDouble(outline.get(index - 1));
            if (Math.abs(step) < EXACT) {
                continue;
            }
            if (lastStep != 0 && Math.signum(step) != Math.signum(lastStep)) {
                turns++;
            }
            lastStep = step;
        }
        return turns;
    }

    private static double width(List<RipRow> outline) {
        return outline.stream().mapToDouble(RipRow::rightAcross).max().orElseThrow()
                - outline.stream().mapToDouble(RipRow::leftAcross).min().orElseThrow();
    }

    private static double height(List<RipRow> outline) {
        return outline.stream().mapToDouble(RipRow::up).max().orElseThrow()
                - outline.stream().mapToDouble(RipRow::up).min().orElseThrow();
    }
}
