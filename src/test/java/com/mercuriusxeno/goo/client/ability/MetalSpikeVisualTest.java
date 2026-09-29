package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MetalSpikeVisual's orb shake: the metal marker shakes through each
 * spike's windup and is still outside it.
 */
class MetalSpikeVisualTest {

    /** The strike tick metal_spikes.json declares. */
    private static final int STRIKE_TICK = 6;
    private static final int STEPS_PER_TICK = 10;

    @Test
    void orbOscillatesAboutRestingSizeThroughTheWindup() {
        int signChanges = 0;
        float previousOffset = 0f;
        for (int step = 1; step < STRIKE_TICK * STEPS_PER_TICK; step++) {
            int age = step / STEPS_PER_TICK;
            float partial = (step % STEPS_PER_TICK) / (float) STEPS_PER_TICK;
            float offset = MetalSpikeVisual.gooShake(age, partial, STRIKE_TICK) - 1f;
            assertTrue(Math.abs(offset) <= MetalSpikeVisual.SHAKE_AMPLITUDE, "shake " + offset + " past its amplitude");
            if (previousOffset != 0f && Math.signum(offset) != Math.signum(previousOffset)) {
                signChanges++;
            }
            if (offset != 0f) {
                previousOffset = offset;
            }
        }
        assertTrue(signChanges >= 2, "the orb swings " + signChanges + " times in the windup");
    }

    @Test
    void orbRestsOnTheStrikeTick() {
        assertEquals(1f, MetalSpikeVisual.gooShake(STRIKE_TICK, 0f, STRIKE_TICK), 0f);
    }

    @Test
    void orbIsStillPastTheStrike() {
        for (int age = STRIKE_TICK; age < STRIKE_TICK * 3; age++) {
            assertEquals(1f, MetalSpikeVisual.gooShake(age, 0.5f, STRIKE_TICK), 0f);
        }
    }

    @Test
    void orbComesToRestContinuouslyAtTheStrike() {
        float lastWindupFrame = MetalSpikeVisual.gooShake(STRIKE_TICK - 1, 0.99f, STRIKE_TICK);
        assertEquals(1f, lastWindupFrame, 0.02f);
    }
}
