package com.mercuriusxeno.goo.ability.reap;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers how a Reap blade's size is drawn: one size sets its width and its
 * depth together, so a narrow blade is skinny and a wide one deep, and no
 * blade is wide and skinny.
 * reap-is-crissaegrim-slashes-that-harvest-and-cut
 */
class BladeSizesTest {

    private static final BladeSizes SIZES = new BladeSizes(1.2, 3.2, 0.5, 1.8);
    private static final double TOLERANCE = 1e-9;

    @Test
    void theSmallestBladeIsNarrowAndSkinny() {
        assertEquals(1.2, SIZES.widthAt(0), TOLERANCE);
        assertEquals(0.5, SIZES.depthAt(0), TOLERANCE);
    }

    @Test
    void theLargestBladeIsWideAndDeep() {
        assertEquals(3.2, SIZES.widthAt(1), TOLERANCE);
        assertEquals(1.8, SIZES.depthAt(1), TOLERANCE);
    }

    @Test
    void aWiderBladeIsAlwaysDeeper() {
        for (int step = 1; step <= 10; step++) {
            double smaller = (step - 1) / 10.0;
            double larger = step / 10.0;
            assertTrue(SIZES.widthAt(larger) > SIZES.widthAt(smaller));
            assertTrue(SIZES.depthAt(larger) > SIZES.depthAt(smaller));
        }
    }
}
