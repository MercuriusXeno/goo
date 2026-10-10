package com.mercuriusxeno.goo.ability.petrify;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * A block's calcify share holds while the fog lingers, then decays slowly
 * back to its floor and clears (decision petrify-stone-encasement-and-calcify-map).
 */
class BlockExposuresTest {

    private static final float DELTA = 1e-6f;
    private static final long NOW = 1_000L;

    @Test
    void aShareHoldsWhileTheFogLingers() {
        BlockExposures.Exposure exposure = new BlockExposures.Exposure(null, 0.5f, NOW);
        assertSame(exposure, BlockExposures.decayed(exposure, NOW + BlockExposures.DECAY_DELAY_TICKS));
    }

    @Test
    void onceTheFogLeavesTheShareDecaysSlowly() {
        BlockExposures.Exposure exposure = new BlockExposures.Exposure(null, 0.5f, NOW);
        BlockExposures.Exposure decayed = BlockExposures.decayed(exposure, NOW + BlockExposures.DECAY_DELAY_TICKS + 1);
        assertEquals(0.5f - BlockExposures.DECAY_PER_TICK, decayed.share(), DELTA);
    }

    // decision decay-gnats-degrade-each-block-once
    @Test
    void aShareLeftToFinishGrowsByItsRateWithNothingReachingIt() {
        float rate = 0.1f;
        BlockExposures.Exposure finishing = new BlockExposures.Exposure(null, 0.6f, NOW, rate);
        BlockExposures.Exposure grown = BlockExposures.decayed(finishing, NOW + BlockExposures.DECAY_DELAY_TICKS * 10);
        assertEquals(0.6f + rate, grown.share(), DELTA);
    }

    // decay-gnats-degrade-each-block-once: Decay's maroon stays on its overlay as the share grows or recedes
    @Test
    void aTintedShareKeepsItsTintGrowingOrDecaying() {
        int maroon = 0xC03434;
        BlockExposures.Exposure finishing = new BlockExposures.Exposure(null, 0.6f, NOW, 0.1f, maroon);
        assertEquals(maroon, BlockExposures.decayed(finishing, NOW + 1).tint());
        BlockExposures.Exposure left = new BlockExposures.Exposure(null, 0.5f, NOW, 0f, maroon);
        assertEquals(maroon, BlockExposures.decayed(left, NOW + BlockExposures.DECAY_DELAY_TICKS + 1).tint());
    }

    @Test
    void aShareDecayedToItsFloorClears() {
        BlockExposures.Exposure nearly = new BlockExposures.Exposure(null, BlockExposures.DECAY_PER_TICK / 2, NOW);
        assertNull(BlockExposures.decayed(nearly, NOW + BlockExposures.DECAY_DELAY_TICKS + 1));
    }
}
