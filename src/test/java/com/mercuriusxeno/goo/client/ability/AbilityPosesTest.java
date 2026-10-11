package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.ArmPoseKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The glove arm's ability poses: the wind-up draws the hand to the
 * opposite shoulder and holds it there, and the fling sweeps the arm out
 * from the shoulder to the glove side, holds, and falls back
 * (decision shards-sling-then-morph-to-flechettes).
 */
class AbilityPosesTest {

    private static final float TOLERANCE = 1e-5f;

    @Test
    void theWindUpDrawsTheHandAcrossToTheShoulderAndHoldsIt() {
        assertEquals(0f, AbilityPoses.heldAt(ArmPoseKind.WIND_UP, 0).weight(), TOLERANCE);
        assertEquals(0.5f, AbilityPoses.heldAt(ArmPoseKind.WIND_UP, AbilityPoses.WIND_UP_TICKS / 2).weight(),
                TOLERANCE);
        AbilityPoses.Held wound = AbilityPoses.heldAt(ArmPoseKind.WIND_UP, 100);
        assertEquals(1f, wound.weight(), TOLERANCE);
        assertEquals(AbilityPoses.WOUND, wound.angles());
        assertTrue(wound.angles().yRot() < 0, "the hand turns across the chest");
        assertFalse(AbilityPoses.overAt(ArmPoseKind.WIND_UP, 1000));
    }

    @Test
    void theFlingSweepsFromTheShoulderOutToTheGloveSideThenFallsBack() {
        assertEquals(AbilityPoses.WOUND, AbilityPoses.heldAt(ArmPoseKind.FLING, 0).angles());
        AbilityPoses.Held out = AbilityPoses.heldAt(ArmPoseKind.FLING, AbilityPoses.FLING_OUT_TICKS);
        assertEquals(AbilityPoses.FLUNG, out.angles());
        assertEquals(1f, out.weight(), TOLERANCE);
        assertTrue(out.angles().yRot() > 0, "the arm flings out to the glove side");
        float end = AbilityPoses.FLING_OUT_TICKS + AbilityPoses.FLING_HOLD_TICKS + AbilityPoses.FLING_RETURN_TICKS;
        assertEquals(0f, AbilityPoses.heldAt(ArmPoseKind.FLING, end).weight(), TOLERANCE);
        assertFalse(AbilityPoses.overAt(ArmPoseKind.FLING, end - 1));
        assertTrue(AbilityPoses.overAt(ArmPoseKind.FLING, end));
    }
}
