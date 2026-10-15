package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Melt's blob lands whole on the face, flattens and spreads across it, and
 * sinks to nothing at the end of the stone's mingle into lava.
 * melt-blob-absorbs-into-lava
 */
class MeltAbsorbsTest {

    private static final float DELTA = 1e-5f;

    @Test
    void theBlobLandsWholeOnTheFace() {
        assertEquals(1f, MeltAbsorbs.across(0f), DELTA);
        assertEquals(1f, MeltAbsorbs.along(0f), DELTA);
        assertEquals(0f, MeltAbsorbs.depth(0f), DELTA);
    }

    @Test
    void theBlobFlattensAndSpreadsAsItMeetsTheFace() {
        float met = MeltAbsorbs.FLATTEN_SHARE;
        assertTrue(MeltAbsorbs.along(met) < MeltAbsorbs.across(met), "flatter along the normal than across");
        assertTrue(MeltAbsorbs.across(met) > 1f - met, "spread wider than its shrink alone");
    }

    @Test
    void theBlobSinksToNothingAsTheMingleEnds() {
        assertEquals(0f, MeltAbsorbs.across(1f), DELTA);
        assertEquals(0f, MeltAbsorbs.along(1f), DELTA);
        assertEquals(MeltAbsorbs.SINK_DEPTH, MeltAbsorbs.depth(1f), DELTA);
    }
}
