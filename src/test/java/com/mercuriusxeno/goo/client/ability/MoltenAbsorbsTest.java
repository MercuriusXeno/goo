package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Molten's blob lands whole on the face, flattens and spreads across it, and
 * sinks to nothing at the end of the stone's mingle into lava.
 * melt-blob-absorbs-into-lava
 */
class MoltenAbsorbsTest {

    private static final float DELTA = 1e-5f;

    @Test
    void theBlobLandsWholeOnTheFace() {
        assertEquals(1f, MoltenAbsorbs.across(0f), DELTA);
        assertEquals(1f, MoltenAbsorbs.along(0f), DELTA);
        assertEquals(0f, MoltenAbsorbs.depth(0f), DELTA);
    }

    @Test
    void theBlobFlattensAndSpreadsAsItMeetsTheFace() {
        float met = MoltenAbsorbs.FLATTEN_SHARE;
        assertTrue(MoltenAbsorbs.along(met) < MoltenAbsorbs.across(met), "flatter along the normal than across");
        assertTrue(MoltenAbsorbs.across(met) > 1f - met, "spread wider than its shrink alone");
    }

    @Test
    void theBlobSinksToNothingAsTheMingleEnds() {
        assertEquals(0f, MoltenAbsorbs.across(1f), DELTA);
        assertEquals(0f, MoltenAbsorbs.along(1f), DELTA);
        assertEquals(MoltenAbsorbs.SINK_DEPTH, MoltenAbsorbs.depth(1f), DELTA);
    }
}
