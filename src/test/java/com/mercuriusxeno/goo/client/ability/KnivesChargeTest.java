package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.ArmPoseKind;
import com.mercuriusxeno.goo.ability.Charge;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Knives' hold: the glove's palm blob swelling with the charge, and the blob
 * held afloat through the wind-up alone (decisions charge-swells-the-palm-blob,
 * knives-blob-floats-ahead-of-the-hand-and-spews-the-knives).
 */
class KnivesChargeTest {

    private static final Charge KNIVES = KnivesCharging.KNIVES;
    private static final float TOLERANCE = 1e-5f;

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
        void theWindUpFloatsTheBlob() {
            assertTrue(KnivesCharge.floatsAt(new AbilityPoses.Posed(ArmPoseKind.WIND_UP, 0, true, 0f)));
        }

        @Test
        void aDroppedArmFloatsTheBlobNoLonger() {
            assertFalse(KnivesCharge.floatsAt(new AbilityPoses.Posed(ArmPoseKind.NONE, 0, true, 0f)));
        }

        @Test
        void aWindUpLetGoFloatsTheBlobNoLonger() {
            assertFalse(KnivesCharge.floatsAt(new AbilityPoses.Posed(ArmPoseKind.WIND_UP, 0, true, 0.5f, true)));
        }
    }

    @Nested
    class PalmShare {

        @Test
        void noKnifeFormingChargeHeldShowsNoShare() {
            assertEquals(0f, KnivesCharge.palmShareOf(null, true, 0.7f), TOLERANCE);
        }

        @Test
        void anArmNotWoundUpShowsNoShare() {
            assertEquals(0f, KnivesCharge.palmShareOf(KNIVES, false, 0.7f), TOLERANCE);
        }

        @Test
        void aWoundUpKnifeFormingChargeShowsTheShareItReached() {
            assertEquals(0.7f, KnivesCharge.palmShareOf(KNIVES, true, 0.7f), TOLERANCE);
            assertEquals(1f, KnivesCharge.palmShareOf(KNIVES, true, 1.5f), TOLERANCE);
        }
    }
}
