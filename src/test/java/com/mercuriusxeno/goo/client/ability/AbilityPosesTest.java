package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.ArmPoseKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The glove arm's ability poses: the wind-up eases the hand left across the
 * chest over the charge, at chest height, and holds it there, and the fling
 * backhands the arm out from wherever the wind-up got to, holds, and falls
 * back; a held-out arm points along the aim, a throw flings forward from it,
 * a raised arm holds the hand up, and a held pose let go eases back down
 * (decisions ability-json-names-its-arm-pose, use-key-throw-swings-no-more).
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
        assertFalse(AbilityPoses.overAt(new AbilityPoses.Posed(ArmPoseKind.WIND_UP, 0, true, 0f), 1000));
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
        AbilityPoses.Posed fling = new AbilityPoses.Posed(ArmPoseKind.FLING, 0, true, from);
        assertFalse(AbilityPoses.overAt(fling, end - 1));
        assertTrue(AbilityPoses.overAt(fling, end));
        assertEquals(0f, AbilityPoses.easedFrom(null, 5), TOLERANCE);
    }

    @Test
    void aHeldOutArmEasesLevelAlongTheAimAndHoldsThere() {
        assertEquals(0f, AbilityPoses.heldAt(ArmPoseKind.HOLD_OUT, 0, 0f).weight(), TOLERANCE);
        AbilityPoses.Held held = AbilityPoses.heldAt(ArmPoseKind.HOLD_OUT, AbilityPoses.HOLD_EASE_TICKS, 0f);
        assertEquals(1f, held.weight(), TOLERANCE);
        assertEquals(AbilityPoses.AIMED, held.angles());
        assertEquals(1f, held.headShare(), TOLERANCE);
        assertEquals(-Math.PI / 2, held.angles().xRot(), TOLERANCE);
        assertFalse(AbilityPoses.overAt(new AbilityPoses.Posed(ArmPoseKind.HOLD_OUT, 0, true, 0f), 1000));
    }

    @Test
    void aRaisedArmHoldsTheHandUpOverheadWithoutFollowingTheAim() {
        AbilityPoses.Held held = AbilityPoses.heldAt(ArmPoseKind.HOLD_UP, AbilityPoses.HOLD_EASE_TICKS, 0f);
        assertEquals(1f, held.weight(), TOLERANCE);
        assertEquals(AbilityPoses.RAISED, held.angles());
        assertTrue(held.angles().xRot() < -Math.PI * 3 / 4, "the hand is raised well above the shoulder");
        assertEquals(0f, held.headShare(), TOLERANCE);
    }

    @Test
    void aThrowFlingsForwardFromTheAimThenFallsBack() {
        AbilityPoses.Held start = AbilityPoses.heldAt(ArmPoseKind.THROW, 0, 1f);
        assertEquals(AbilityPoses.AIMED, start.angles());
        assertEquals(1f, start.headShare(), TOLERANCE);
        AbilityPoses.Held out = AbilityPoses.heldAt(ArmPoseKind.THROW, AbilityPoses.THROW_OUT_TICKS, 1f);
        assertEquals(AbilityPoses.FOLLOW_THROUGH, out.angles());
        assertEquals(0f, out.headShare(), TOLERANCE);
        assertTrue(out.angles().xRot() > AbilityPoses.AIMED.xRot(), "the arm follows through below the aim");
        float end = AbilityPoses.THROW_OUT_TICKS + AbilityPoses.THROW_HOLD_TICKS + AbilityPoses.FLING_RETURN_TICKS;
        assertEquals(0f, AbilityPoses.heldAt(ArmPoseKind.THROW, end, 1f).weight(), TOLERANCE);
        AbilityPoses.Posed thrown = new AbilityPoses.Posed(ArmPoseKind.THROW, 0, true, 1f);
        assertFalse(AbilityPoses.overAt(thrown, end - 1));
        assertTrue(AbilityPoses.overAt(thrown, end));
    }

    @Test
    void aHeldPoseLetGoEasesDownFromWhereItGotTo() {
        AbilityPoses.Posed holding = new AbilityPoses.Posed(ArmPoseKind.HOLD_OUT, 0, true, 0f);
        AbilityPoses.Posed lowering = AbilityPoses.posedNext(holding, ArmPoseKind.NONE, true, 10);
        assertTrue(lowering.lowering());
        assertEquals(ArmPoseKind.HOLD_OUT, lowering.kind());
        assertEquals(1f, AbilityPoses.heldAt(lowering, 0).weight(), TOLERANCE);
        assertEquals(1f, AbilityPoses.heldAt(lowering, 0).headShare(), TOLERANCE);
        float halfway = AbilityPoses.heldAt(lowering, AbilityPoses.LOWER_TICKS / 2).weight();
        assertTrue(halfway > 0f && halfway < 1f, "the arm eases rather than snapping, at " + halfway);
        assertEquals(0f, AbilityPoses.heldAt(lowering, AbilityPoses.LOWER_TICKS).weight(), TOLERANCE);
        assertTrue(AbilityPoses.overAt(lowering, AbilityPoses.LOWER_TICKS));
    }

    @Test
    void aFlingOrAThrowToldNothingPlaysOn() {
        AbilityPoses.Posed fling = new AbilityPoses.Posed(ArmPoseKind.FLING, 0, true, 0f);
        assertSame(fling, AbilityPoses.posedNext(fling, ArmPoseKind.NONE, true, 1));
        AbilityPoses.Posed thrown = new AbilityPoses.Posed(ArmPoseKind.THROW, 0, true, 0f);
        assertSame(thrown, AbilityPoses.posedNext(thrown, ArmPoseKind.NONE, true, 1));
        assertNull(AbilityPoses.posedNext(null, ArmPoseKind.NONE, true, 1));
    }

    @Test
    void theReleaseStartsFromWhereTheHoldGotTo() {
        AbilityPoses.Posed holding = new AbilityPoses.Posed(ArmPoseKind.HOLD_OUT, 0, true, 0f);
        AbilityPoses.Posed thrown = AbilityPoses.posedNext(holding, ArmPoseKind.THROW, true, 20);
        assertEquals(ArmPoseKind.THROW, thrown.kind());
        assertEquals(20, thrown.since(), TOLERANCE);
        assertEquals(1f, thrown.from(), TOLERANCE);
        assertSame(holding, AbilityPoses.posedNext(holding, ArmPoseKind.HOLD_OUT, true, 20));
    }

    @Test
    void aFlingOverIsReplacedByTheNextFling() {
        AbilityPoses.Posed done = new AbilityPoses.Posed(ArmPoseKind.FLING, 0, true, 0f);
        AbilityPoses.Posed again = AbilityPoses.posedNext(done, ArmPoseKind.FLING, true, 100);
        assertEquals(100, again.since(), TOLERANCE);
        assertEquals(0f, again.from(), TOLERANCE);
    }
}
