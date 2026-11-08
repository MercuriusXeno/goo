package com.mercuriusxeno.goo.ability.deep;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Submerged in blocks, a Sculker hears sounds from anywhere within render
 * distance: the server carries a sound to a submerged diver past the range
 * vanilla sends it, out to the edge of render distance and no farther, and
 * to a diver out in the open not at all
 * (decision sculker-sight-persists-where-echoes-have-touched).
 */
class SculkerHearingTest {

    private static final double VANILLA_RANGE = 16;
    private static final double VIEW_REACH = 160;

    @Test
    void aSubmergedDiverHearsASoundPastVanillasRange() {
        assertTrue(SculkerHearing.carriesTo(true, 100, VANILLA_RANGE, VIEW_REACH));
    }

    @Test
    void aSoundWithinVanillasRangeIsLeftToVanilla() {
        assertFalse(SculkerHearing.carriesTo(true, 10, VANILLA_RANGE, VIEW_REACH));
    }

    @Test
    void aSoundPastRenderDistanceIsNotCarried() {
        assertFalse(SculkerHearing.carriesTo(true, VIEW_REACH + 1, VANILLA_RANGE, VIEW_REACH));
    }

    @Test
    void aDiverOutInTheOpenHearsNoFarSound() {
        assertFalse(SculkerHearing.carriesTo(false, 100, VANILLA_RANGE, VIEW_REACH));
    }
}
