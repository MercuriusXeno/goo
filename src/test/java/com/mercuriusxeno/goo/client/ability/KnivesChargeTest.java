package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.ArmPoseKind;
import com.mercuriusxeno.goo.ability.Charge;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Knives' charge: a chime at each step as the charge grows, capped at the
 * steps it chimes, the glove's palm blob swelling with the charge, and the
 * blob held afloat through the wind-up and the backhand's sweep (decisions
 * knives-sling-then-morph-to-flechettes, charge-swells-the-palm-blob,
 * knives-blob-floats-ahead-of-the-hand-and-spews-the-knives).
 */
class KnivesChargeTest {

    private static final Charge KNIVES = new Charge(30, 27, 45, 8, 3, 5);
    private static final float TOLERANCE = 1e-5f;

    @Nested
    class ChimeSteps {

        @Test
        void theChargeStepsUpAsItGrowsUpToTheStepsItChimes() {
            assertEquals(3, KnivesCharge.shownAt(KNIVES, 0f));
            assertEquals(8, KnivesCharge.shownAt(KNIVES, 0.2f));
            assertEquals(KnivesCharge.MOST_SHOWN, KnivesCharge.shownAt(KNIVES, 1f));
        }
    }

    @Nested
    class PalmBlobScale {

        @Test
        void thePalmBlobRestsAtItsOwnSizeWithNoCharge() {
            assertEquals(KnivesCharge.PALM_REST, KnivesCharge.palmScaleAt(0f), TOLERANCE);
        }

        @Test
        void thePalmBlobSitsMidwayAtHalfACharge() {
            assertEquals((KnivesCharge.PALM_REST + KnivesCharge.PALM_FULLEST) / 2f,
                    KnivesCharge.palmScaleAt(0.5f), TOLERANCE);
        }

        @Test
        void thePalmBlobSwellsToItsFullestAtFullChargeAndNoFurther() {
            assertEquals(KnivesCharge.PALM_FULLEST, KnivesCharge.palmScaleAt(1f), TOLERANCE);
            assertEquals(KnivesCharge.PALM_FULLEST, KnivesCharge.palmScaleAt(2f), TOLERANCE);
        }
    }

    @Nested
    class FloatsTheBlob {

        @Test
        void theWindUpFloatsTheBlobHoweverLongItIsHeld() {
            assertTrue(KnivesCharge.floatsAt(new AbilityPoses.Posed(ArmPoseKind.WIND_UP, 0, true, 0f), 100,
                    KNIVES.sweepTicks()));
        }

        @Test
        void theBackhandFloatsTheBlobThroughItsSweepAndNoLonger() {
            AbilityPoses.Posed backhand = new AbilityPoses.Posed(ArmPoseKind.FLING, 0, true, 0.8f);

            assertTrue(KnivesCharge.floatsAt(backhand, KNIVES.sweepTicks(), KNIVES.sweepTicks()),
                    "at the sweep's last tick the blob should float");
            assertFalse(KnivesCharge.floatsAt(backhand, KNIVES.sweepTicks() + 1, KNIVES.sweepTicks()),
                    "past the sweep the blob should settle");
        }

        @Test
        void throughTheBackhandTheBlobHoldsTheReachTheWindUpGotToWhileTheHandSweepsOut() {
            AbilityPoses.Posed backhand = new AbilityPoses.Posed(ArmPoseKind.FLING, 0, true, 0.8f);

            for (int age = 0; age <= KNIVES.sweepTicks(); age++) {
                assertEquals(0.8f, KnivesCharge.floatReachAt(backhand, age), TOLERANCE, "at tick " + age);
            }
            assertTrue(AbilityPoses.acrossAt(backhand, KNIVES.sweepTicks()) < 0.8f, "the hand itself sweeps out");
        }

        @Test
        void throughTheWindUpTheBlobTravelsWithTheHand() {
            AbilityPoses.Posed windUp = new AbilityPoses.Posed(ArmPoseKind.WIND_UP, 0, true, 0f);

            assertEquals(AbilityPoses.acrossAt(windUp, 12), KnivesCharge.floatReachAt(windUp, 12), TOLERANCE);
        }

        @Test
        void aWindUpLetGoFloatsTheBlobNoLonger() {
            assertFalse(KnivesCharge.floatsAt(new AbilityPoses.Posed(ArmPoseKind.WIND_UP, 0, true, 0.5f, true), 1,
                    KNIVES.sweepTicks()));
        }
    }

    @Nested
    class PalmShare {

        @Test
        void noSlingingChargeHeldShowsNoShare() {
            assertEquals(0f, KnivesCharge.palmShareOf(null, true, 0.7f), TOLERANCE);
        }

        @Test
        void anArmNotWoundUpShowsNoShare() {
            assertEquals(0f, KnivesCharge.palmShareOf(KNIVES, false, 0.7f), TOLERANCE);
        }

        @Test
        void aWoundUpSlingingChargeShowsTheShareItReached() {
            assertEquals(0.7f, KnivesCharge.palmShareOf(KNIVES, true, 0.7f), TOLERANCE);
            assertEquals(1f, KnivesCharge.palmShareOf(KNIVES, true, 1.5f), TOLERANCE);
        }
    }
}
