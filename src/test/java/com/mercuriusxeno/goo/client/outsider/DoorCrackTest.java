package com.mercuriusxeno.goo.client.outsider;

import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the conjured door's outline: a thin crack standing nearly the
 * doorway's height while small, widening to the one wide, two tall doorway
 * at full size, never narrowing as it grows
 * (decision door-opens-from-a-thin-crack-in-the-air).
 */
class DoorCrackTest {

    private static final double FULL_WIDE = 1.0;
    private static final double FULL_TALL = 2.0;
    private static final double EXACT = 1.0E-9;

    @Test
    void aSmallDoorIsACrackFarTallerThanWide() {
        List<DoorCrack.Corner> outline = DoorCrack.outline(0.1);

        double heightShare = height(outline) / FULL_TALL;
        double widthShare = width(outline) / FULL_WIDE;
        assertTrue(heightShare >= 4 * widthShare,
                "height share " + heightShare + " against width share " + widthShare);
    }

    @Test
    void aFullDoorIsTheDoorwayOneWideTwoTall() {
        List<DoorCrack.Corner> outline = DoorCrack.outline(1);

        assertEquals(FULL_WIDE, width(outline), EXACT, "wide");
        assertEquals(FULL_TALL, height(outline), EXACT, "tall");
    }

    @Test
    void atEverySizeTheCrackIsAFullHeightRectangleCenteredOnTheDoorway() {
        for (int step = 0; step <= 20; step++) {
            double size = step / 20.0;
            List<DoorCrack.Corner> outline = DoorCrack.outline(size);
            for (DoorCrack.Corner corner : outline) {
                assertEquals(FULL_WIDE * size / 2, Math.abs(corner.across()), EXACT,
                        "at size " + size + " every corner stands on a side, not a point: " + corner);
                assertTrue(Math.abs(corner.up()) < EXACT || Math.abs(corner.up() - FULL_TALL) < EXACT,
                        "at size " + size + " every corner stands on the foot or the head: " + corner);
            }
            assertEquals(FULL_TALL, height(outline), EXACT, "the full height from the start, at size " + size);
        }
    }

    @Test
    void theCrackNeverNarrowsAsItOpens() {
        double was = 0;
        for (int step = 0; step <= 20; step++) {
            double size = step / 20.0;
            double wide = width(DoorCrack.outline(size));
            assertTrue(wide >= was, "at size " + size + " the width " + wide + " fell below " + was);
            was = wide;
        }
    }

    private static double width(List<DoorCrack.Corner> outline) {
        return outline.stream().mapToDouble(DoorCrack.Corner::across).max().orElseThrow()
                - outline.stream().mapToDouble(DoorCrack.Corner::across).min().orElseThrow();
    }

    private static double height(List<DoorCrack.Corner> outline) {
        return outline.stream().mapToDouble(DoorCrack.Corner::up).max().orElseThrow()
                - outline.stream().mapToDouble(DoorCrack.Corner::up).min().orElseThrow();
    }
}
