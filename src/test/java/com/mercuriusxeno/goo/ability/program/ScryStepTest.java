package com.mercuriusxeno.goo.ability.program;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ScryStep's front: the radius it grows to with the hold, capped at its
 * reach, and the distances one tick's front crosses.
 * decision scry-sphere-reveals-faces-and-glistens-mobs
 */
class ScryStepTest {

    private static final ScryStep SCRY = new ScryStep(1, 48, List.of(), List.of());

    @Test
    void radiusGrowsABlockATickUpToItsReach() {
        assertEquals(0, SCRY.radiusAt(0), 0);
        assertEquals(20, SCRY.radiusAt(20), 0);
        assertEquals(48, SCRY.radiusAt(60), 0);
    }

    @Test
    void frontCrossesWhatLiesPastLastTicksRadiusAndWithinThisOnes() {
        assertTrue(ScryStep.crossed(4.5, 4, 5));
        assertTrue(ScryStep.crossed(5, 4, 5));
        assertFalse(ScryStep.crossed(4, 4, 5));
        assertFalse(ScryStep.crossed(5.1, 4, 5));
    }

    @Test
    void aFrontAtItsReachCrossesNothing() {
        assertFalse(ScryStep.crossed(48, SCRY.radiusAt(60), SCRY.radiusAt(61)));
    }
}
