package com.mercuriusxeno.goo.ability.deep;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * While Sculker stands its holder hears sounds from anywhere within render
 * distance, in the open as inside blocks: the server carries a sound past
 * the range vanilla sends it, out to the edge of render distance and no
 * farther (decision sculker-sight-persists-where-echoes-have-touched).
 */
class SculkerHearingTest {

    private static final double VANILLA_RANGE = 16;
    private static final double VIEW_REACH = 160;

    @Test
    void aSculkerHearsASoundPastVanillasRange() {
        assertTrue(SculkerHearing.carriesTo(100, VANILLA_RANGE, VIEW_REACH));
    }

    @Test
    void aSoundWithinVanillasRangeIsLeftToVanilla() {
        assertFalse(SculkerHearing.carriesTo(10, VANILLA_RANGE, VIEW_REACH));
    }

    @Test
    void aSoundPastRenderDistanceIsNotCarried() {
        assertFalse(SculkerHearing.carriesTo(VIEW_REACH + 1, VANILLA_RANGE, VIEW_REACH));
    }

    /** Only a vibration ranked above 5 lends a sound more than it rings at unranked (decision sculker-echo-intensity-follows-vibration-rank). */
    @Test
    void onlyAVibrationRankedAboveFiveIsForwarded() {
        assertTrue(SculkerHearing.forwards(6));
        assertFalse(SculkerHearing.forwards(5));
        assertFalse(SculkerHearing.forwards(0));
    }
}
