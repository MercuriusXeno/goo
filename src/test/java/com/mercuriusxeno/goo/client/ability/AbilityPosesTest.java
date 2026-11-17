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
 * The glove arm's ability poses: a held-out arm points along the aim, a
 * raised arm holds the hand at face height, and a held pose let go eases
 * back down
 * (decisions ability-json-names-its-arm-pose, use-key-throw-swings-no-more).
 */
class AbilityPosesTest {

    private static final float TOLERANCE = 1e-5f;

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
    void noPoseToldNothingStaysNone() {
        assertNull(AbilityPoses.posedNext(null, ArmPoseKind.NONE, true, 1));
        assertEquals(0f, AbilityPoses.easedFrom(null, 5), TOLERANCE);
    }

    @Test
    void aNewFrameStartsFromWhereTheHoldGotTo() {
        AbilityPoses.Posed holding = new AbilityPoses.Posed(ArmPoseKind.HOLD_OUT, 0, true, 0f);
        AbilityPoses.Posed raised = AbilityPoses.posedNext(holding, ArmPoseKind.HOLD_UP, true,
                AbilityPoses.HOLD_EASE_TICKS);
        assertEquals(ArmPoseKind.HOLD_UP, raised.kind());
        assertEquals(AbilityPoses.HOLD_EASE_TICKS, raised.since(), TOLERANCE);
        assertEquals(1f, raised.from(), TOLERANCE);
        assertSame(holding, AbilityPoses.posedNext(holding, ArmPoseKind.HOLD_OUT, true, 20));
    }

    @Test
    void aPoseEasedDownIsReplacedByTheNextHold() {
        AbilityPoses.Posed done = new AbilityPoses.Posed(ArmPoseKind.HOLD_OUT, 0, true, 0f, true);
        AbilityPoses.Posed again = AbilityPoses.posedNext(done, ArmPoseKind.HOLD_OUT, true, 100);
        assertEquals(100, again.since(), TOLERANCE);
        assertEquals(0f, again.from(), TOLERANCE);
    }

    /** The first-person glove hand moves on every pose. */
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
    }
}
