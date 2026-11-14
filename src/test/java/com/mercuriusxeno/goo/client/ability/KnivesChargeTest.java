package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.Charge;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Knives' charge: a chime at each step as the charge grows, capped at the
 * steps it chimes, a goo blob in a remote hand and the glove's palm blob
 * swelling with the charge (decisions knives-sling-then-morph-to-flechettes,
 * charge-swells-the-palm-blob).
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
    class WorldBlobScale {

        @Test
        void theGooInTheHandSwellsWithTheCharge() {
            assertEquals(KnivesCharge.BLOB_SMALLEST, KnivesCharge.blobScaleAt(0f), TOLERANCE);
            assertTrue(KnivesCharge.blobScaleAt(0.5f) > KnivesCharge.blobScaleAt(0.25f));
            assertEquals(KnivesCharge.BLOB_FULLEST, KnivesCharge.blobScaleAt(1f), TOLERANCE);
            assertEquals(KnivesCharge.BLOB_FULLEST, KnivesCharge.blobScaleAt(2f), TOLERANCE);
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
