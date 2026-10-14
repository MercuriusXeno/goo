package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.ArmPoseKind;
import org.junit.jupiter.api.Nested;
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
 * back; a held-out arm points along the aim, a raised arm holds the hand at
 * face height, and a held pose let go eases back down
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
    void aRaisedArmHoldsTheHandAtFaceHeightWithoutFollowingTheAim() {
        AbilityPoses.Held held = AbilityPoses.heldAt(ArmPoseKind.HOLD_UP, AbilityPoses.HOLD_EASE_TICKS, 0f);
        assertEquals(1f, held.weight(), TOLERANCE);
        assertEquals(AbilityPoses.RAISED, held.angles());
        assertTrue(held.angles().xRot() < -Math.PI / 2, "the hand is raised above level");
        assertTrue(held.angles().xRot() > -Math.PI * 3 / 4, "the hand stops at face height, short of overhead");
        assertEquals(0f, held.headShare(), TOLERANCE);
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
    void aFlingToldNothingPlaysOn() {
        AbilityPoses.Posed fling = new AbilityPoses.Posed(ArmPoseKind.FLING, 0, true, 0f);
        assertSame(fling, AbilityPoses.posedNext(fling, ArmPoseKind.NONE, true, 1));
        assertNull(AbilityPoses.posedNext(null, ArmPoseKind.NONE, true, 1));
    }

    @Test
    void theReleaseStartsFromWhereTheHoldGotTo() {
        AbilityPoses.Posed holding = new AbilityPoses.Posed(ArmPoseKind.WIND_UP, 0, true, 0f);
        AbilityPoses.Posed flung = AbilityPoses.posedNext(holding, ArmPoseKind.FLING, true, AbilityPoses.WIND_UP_TICKS);
        assertEquals(ArmPoseKind.FLING, flung.kind());
        assertEquals(AbilityPoses.WIND_UP_TICKS, flung.since(), TOLERANCE);
        assertEquals(1f, flung.from(), TOLERANCE);
        assertSame(holding, AbilityPoses.posedNext(holding, ArmPoseKind.WIND_UP, true, 20));
    }

    @Test
    void aFlingOverIsReplacedByTheNextFling() {
        AbilityPoses.Posed done = new AbilityPoses.Posed(ArmPoseKind.FLING, 0, true, 0f);
        AbilityPoses.Posed again = AbilityPoses.posedNext(done, ArmPoseKind.FLING, true, 100);
        assertEquals(100, again.since(), TOLERANCE);
        assertEquals(0f, again.from(), TOLERANCE);
    }

    /** The first-person glove hand moves on every pose, not on the wind-up and fling alone. */
    @Nested
    class FirstPersonHand {

        private static final double PAST_THE_EASE = 1000;
        /** The most the raised hand lifts, in screen blocks: 0.3 showed the arm's cut end in a dev instance. */
        private static final float A_LITTLE = 0.15f;

        private AbilityPoses.HandOffset handAt(ArmPoseKind kind, float from, double age) {
            return AbilityPoses.handAt(new AbilityPoses.Posed(kind, 0, true, from), age);
        }

        private void assertHand(AbilityPoses.HandOffset expected, AbilityPoses.HandOffset actual) {
            assertEquals(expected.across(), actual.across(), TOLERANCE);
            assertEquals(expected.lift(), actual.lift(), TOLERANCE);
            assertEquals(expected.forward(), actual.forward(), TOLERANCE);
            assertEquals(expected.turnDegrees(), actual.turnDegrees(), TOLERANCE);
            assertEquals(expected.tiltDegrees(), actual.tiltDegrees(), TOLERANCE);
        }

        @Test
        void heldOutPushesTheHandForwardTowardTheCrosshairAndHoldsIt() {
            assertHand(AbilityPoses.VANILLA_HAND, handAt(ArmPoseKind.HOLD_OUT, 0f, 0));
            float halfway = handAt(ArmPoseKind.HOLD_OUT, 0f, AbilityPoses.HOLD_EASE_TICKS / 2).forward();
            assertTrue(halfway > 0f && halfway < AbilityPoses.HELD_OUT_HAND.forward(),
                    "the hand eases forward rather than snapping, at " + halfway);
            assertHand(AbilityPoses.HELD_OUT_HAND, handAt(ArmPoseKind.HOLD_OUT, 0f, AbilityPoses.HOLD_EASE_TICKS));
            assertHand(AbilityPoses.HELD_OUT_HAND, handAt(ArmPoseKind.HOLD_OUT, 0f, PAST_THE_EASE));
            assertTrue(AbilityPoses.HELD_OUT_HAND.forward() > 0f, "the hand pushes away from the camera");
            assertTrue(AbilityPoses.HELD_OUT_HAND.across() < 0f, "the hand moves in toward the crosshair");
        }

        @Test
        void heldUpLiftsTheHandAndHoldsIt() {
            assertHand(AbilityPoses.VANILLA_HAND, handAt(ArmPoseKind.HOLD_UP, 0f, 0));
            float halfway = handAt(ArmPoseKind.HOLD_UP, 0f, AbilityPoses.HOLD_EASE_TICKS / 2).lift();
            assertTrue(halfway > 0f && halfway < AbilityPoses.RAISED_HAND.lift(),
                    "the hand eases up rather than snapping, at " + halfway);
            assertHand(AbilityPoses.RAISED_HAND, handAt(ArmPoseKind.HOLD_UP, 0f, PAST_THE_EASE));
            assertTrue(AbilityPoses.RAISED_HAND.lift() > 0f, "the hand lifts");
        }

        @Test
        void heldUpLiftsTheHandOnlyALittleAndLetsItDownToVanilla() {
            assertTrue(AbilityPoses.RAISED_HAND.lift() <= A_LITTLE,
                    "the hand stays inside the screen, lifted " + AbilityPoses.RAISED_HAND.lift());
            AbilityPoses.Posed raised = new AbilityPoses.Posed(ArmPoseKind.HOLD_UP, 0, true, 0f);
            AbilityPoses.Posed lowering = AbilityPoses.posedNext(raised, ArmPoseKind.NONE, true, PAST_THE_EASE);
            assertHand(AbilityPoses.RAISED_HAND, AbilityPoses.handAt(lowering, 0));
            assertTrue(AbilityPoses.overAt(lowering, AbilityPoses.LOWER_TICKS));
            assertHand(AbilityPoses.VANILLA_HAND, AbilityPoses.handAt(lowering, AbilityPoses.LOWER_TICKS));
        }

        @Test
        void theWindUpAndFlingStillMoveTheHandAcrossThenOut() {
            assertHand(AbilityPoses.WOUND_HAND, handAt(ArmPoseKind.WIND_UP, 0f, AbilityPoses.WIND_UP_TICKS));
            assertHand(AbilityPoses.WOUND_HAND, handAt(ArmPoseKind.FLING, 1f, 0));
            assertHand(AbilityPoses.FLUNG_HAND, handAt(ArmPoseKind.FLING, 1f, AbilityPoses.FLING_OUT_TICKS));
        }
    }
}
