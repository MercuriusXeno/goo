package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A lurker's orb beats faster and glows brighter the nearer its enemy, carries
 * its beat across pulses, and rests once the pulses stop
 * (decision lurker-blob-brightens-then-detonates).
 */
class LurkerPulsesTest {

    private static final BlockPos MARKER = new BlockPos(1, 2, 3);
    private static final float DELTA = 1e-5f;

    @Test
    void theBeatQuickensAsTheEnemyCloses() {
        assertEquals(LurkerPulses.SLOWEST_BEAT_TICKS, LurkerPulses.beatTicks(0f), DELTA);
        assertEquals(LurkerPulses.FASTEST_BEAT_TICKS, LurkerPulses.beatTicks(1f), DELTA);
        assertTrue(LurkerPulses.beatTicks(0.7f) < LurkerPulses.beatTicks(0.3f));
    }

    @Test
    void theGlowRisesWithClosenessAtEveryPointOfTheBeat() {
        for (double phase = 0; phase < 1; phase += 0.125) {
            assertTrue(LurkerPulses.glow(0.8f, phase) > LurkerPulses.glow(0.4f, phase), "phase " + phase);
        }
        assertEquals(0f, LurkerPulses.glow(0f, 0.25), DELTA);
        assertEquals(1f, LurkerPulses.glow(1f, 0.25), DELTA);
    }

    @Test
    void aFreshPulseCarriesTheBeatOnAtTheOldTempo() {
        LurkerPulses pulses = new LurkerPulses();
        pulses.record(MARKER, 0f, 100);
        pulses.record(MARKER, 1f, 104);

        float expected = LurkerPulses.glow(1f, 4 / LurkerPulses.SLOWEST_BEAT_TICKS
                + 1 / LurkerPulses.FASTEST_BEAT_TICKS);
        assertEquals(expected, pulses.glowAt(MARKER, 105f), DELTA);
    }

    @Test
    void theOrbRestsOnceThePulsesStop() {
        LurkerPulses pulses = new LurkerPulses();
        pulses.record(MARKER, 1f, 100);

        assertTrue(pulses.glowAt(MARKER, 100 + LurkerPulses.STALE_TICKS) > 0f);
        assertEquals(0f, pulses.glowAt(MARKER, 101 + LurkerPulses.STALE_TICKS), DELTA);
        assertEquals(0f, pulses.glowAt(BlockPos.ZERO, 100f), DELTA);
    }
}
