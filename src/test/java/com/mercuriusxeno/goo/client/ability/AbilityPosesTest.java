package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.ArmPoseKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The glove arm's ability poses: the wind-up eases the hand left across the
 * chest over the charge, at chest height, and holds it there, and the fling
 * backhands the arm out from wherever the wind-up got to, holds, and falls
 * back (decision ability-json-names-its-arm-pose).
 */
class AbilityPosesTest {

    private static final float TOLERANCE = 1e-5f;

    @Test
    void theWindUpEasesTheHandAcrossTheChestOverTheChargeAndHoldsIt() {
        assertEquals(0f, AbilityPoses.heldAt(ArmPoseKind.WIND_UP, 0, 0f).weight(), TOLERANCE);
        float quarter = AbilityPoses.heldAt(ArmPoseKind.WIND_UP, AbilityPoses.WIND_UP_TICKS / 4, 0f).weight();
        assertTrue(quarter > 0f && quarter < 0.5f, "the hand eases rather than snapping, at " + quarter);
        assertEquals(0.5f, AbilityPoses.heldAt(ArmPoseKind.WIND_UP, AbilityPoses.WIND_UP_TICKS / 2, 0f).weight(),
                TOLERANCE);
        AbilityPoses.Held wound = AbilityPoses.heldAt(ArmPoseKind.WIND_UP, AbilityPoses.WIND_UP_TICKS, 0f);
        assertEquals(1f, wound.weight(), TOLERANCE);
        assertEquals(AbilityPoses.WOUND, wound.angles());
        assertTrue(wound.angles().yRot() < 0, "the hand turns across the chest");
        assertTrue(wound.angles().xRot() > -Math.PI / 2, "the hand stays below the shoulder");
        assertFalse(AbilityPoses.overAt(ArmPoseKind.WIND_UP, 1000));
    }

    @Test
    void theFlingBackhandsFromWhereTheWindUpGotToThenFallsBack() {
        AbilityPoses.Posed halfWound = new AbilityPoses.Posed(ArmPoseKind.WIND_UP, 0, true, 0f);
        float from = AbilityPoses.easedFrom(halfWound, AbilityPoses.WIND_UP_TICKS / 2);
        assertEquals(0.5f, from, TOLERANCE);
        AbilityPoses.Held start = AbilityPoses.heldAt(ArmPoseKind.FLING, 0, from);
        assertEquals(AbilityPoses.WOUND, start.angles());
        assertEquals(from, start.weight(), TOLERANCE);
        AbilityPoses.Held out = AbilityPoses.heldAt(ArmPoseKind.FLING, AbilityPoses.FLING_OUT_TICKS, from);
        assertEquals(AbilityPoses.FLUNG, out.angles());
        assertEquals(1f, out.weight(), TOLERANCE);
        assertTrue(out.angles().yRot() > 0, "the arm flings out to the glove side");
        float end = AbilityPoses.FLING_OUT_TICKS + AbilityPoses.FLING_HOLD_TICKS + AbilityPoses.FLING_RETURN_TICKS;
        assertEquals(0f, AbilityPoses.heldAt(ArmPoseKind.FLING, end, from).weight(), TOLERANCE);
        assertFalse(AbilityPoses.overAt(ArmPoseKind.FLING, end - 1));
        assertTrue(AbilityPoses.overAt(ArmPoseKind.FLING, end));
        assertEquals(0f, AbilityPoses.easedFrom(null, 5), TOLERANCE);
    }
}
