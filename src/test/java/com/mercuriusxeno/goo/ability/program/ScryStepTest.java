package com.mercuriusxeno.goo.ability.program;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ScryStep's front: the distances one tick's front crosses.
 * decision scry-sphere-reveals-faces-and-glistens-mobs
 */
class ScryStepTest {

    /** Scry pings as each pulse leaves the caster, on the pulse's first tick alone (operator ruling 2026-11-06). */
    @Test
    void scryPingsOnEachPulsesFirstTickAlone() {
        HeldSweepPulses.Pulse pulse = new HeldSweepPulses.Pulse(1, net.minecraft.world.phys.Vec3.ZERO, 100);

        assertTrue(ScryStep.pingsOn(pulse, 100), "as the pulse leaves");
        assertFalse(ScryStep.pingsOn(pulse, 101), "not as it travels");
    }

    @Test
    void frontCrossesWhatLiesPastLastTicksRadiusAndWithinThisOnes() {
        assertTrue(ScryStep.crossed(4.5, 4, 5));
        assertTrue(ScryStep.crossed(5, 4, 5));
        assertFalse(ScryStep.crossed(4, 4, 5));
        assertFalse(ScryStep.crossed(5.1, 4, 5));
    }
}
