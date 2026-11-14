package com.mercuriusxeno.goo.client.zone;

import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the conjured door's outline: a pixel at the doorway's head that
 * unzips down to the floor as a slash, then widens to the left and right
 * into the one wide, two tall doorway
 * (decision door-unzips-from-a-starfield-pixel-then-widens-into-you).
 */
class DoorCrackTest {

    private static final double PIXEL = 1.0 / 16;
    private static final double FULL_WIDE = 1.0;
    private static final double FULL_TALL = 2.0;
    private static final double EXACT = 1.0E-9;

    @Test
    void theDoorStartsAsAPixelAtTheDoorwaysHead() {
        List<DoorCrack.Corner> outline = DoorCrack.outline(0, 0);

        assertEquals(PIXEL, width(outline), EXACT, "a pixel wide");
        assertEquals(PIXEL, height(outline), EXACT, "a pixel tall");
        assertEquals(0, center(outline), EXACT, "centered on the doorway");
        assertEquals(FULL_TALL, top(outline), EXACT, "at the doorway's head");
    }

    @Test
    void theSlashUnzipsFromTheHeadDownToTheFloor() {
        double was = Double.MAX_VALUE;
        for (int step = 0; step <= 20; step++) {
            double unzipped = step / 20.0;
            List<DoorCrack.Corner> outline = DoorCrack.outline(unzipped, 0);

            assertEquals(FULL_TALL, top(outline), EXACT, "the head stays put at unzipped " + unzipped);
            assertTrue(bottom(outline) <= was, "the lower edge falls at unzipped " + unzipped);
            assertEquals(PIXEL, width(outline), EXACT, "a pixel wide at unzipped " + unzipped);
            was = bottom(outline);
        }
        assertEquals(0, bottom(DoorCrack.outline(1, 0)), EXACT, "on the floor once unzipped");
    }

    @Test
    void theUnzippedSlashWidensAboutItsCenterline() {
        double was = 0;
        for (int step = 0; step <= 20; step++) {
            double widened = step / 20.0;
            List<DoorCrack.Corner> outline = DoorCrack.outline(1, widened);

            assertEquals(FULL_TALL, height(outline), EXACT, "the full height at widened " + widened);
            assertEquals(0, center(outline), EXACT, "an equal reach each side at widened " + widened);
            assertTrue(width(outline) >= was, "never narrowing at widened " + widened);
            was = width(outline);
        }
        assertEquals(FULL_WIDE, width(DoorCrack.outline(1, 1)), EXACT, "the doorway's width once widened");
    }

    private static double width(List<DoorCrack.Corner> outline) {
        return outline.stream().mapToDouble(DoorCrack.Corner::across).max().orElseThrow()
                - outline.stream().mapToDouble(DoorCrack.Corner::across).min().orElseThrow();
    }

    private static double center(List<DoorCrack.Corner> outline) {
        return (outline.stream().mapToDouble(DoorCrack.Corner::across).max().orElseThrow()
                + outline.stream().mapToDouble(DoorCrack.Corner::across).min().orElseThrow()) / 2;
    }

    private static double top(List<DoorCrack.Corner> outline) {
        return outline.stream().mapToDouble(DoorCrack.Corner::up).max().orElseThrow();
    }

    private static double bottom(List<DoorCrack.Corner> outline) {
        return outline.stream().mapToDouble(DoorCrack.Corner::up).min().orElseThrow();
    }

    private static double height(List<DoorCrack.Corner> outline) {
        return top(outline) - bottom(outline);
    }
}
