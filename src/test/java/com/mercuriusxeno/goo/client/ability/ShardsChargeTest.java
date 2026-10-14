package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.Charge;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shards' charge: a chime at each step as the charge grows, capped at the
 * steps it chimes, a goo blob in a remote hand and the glove's palm blob
 * swelling with the charge (decisions shards-sling-then-morph-to-flechettes,
 * charge-swells-the-palm-blob).
 */
class ShardsChargeTest {

    private static final Charge SHARDS = new Charge(30, 24, 50, 8);
    private static final float TOLERANCE = 1e-5f;

    @Nested
    class ChimeSteps {

        @Test
        void theChargeStepsUpAsItGrowsUpToTheStepsItChimes() {
            assertEquals(1, ShardsCharge.shownAt(SHARDS, 0f));
            assertEquals(5, ShardsCharge.shownAt(SHARDS, 0.2f));
            assertEquals(ShardsCharge.MOST_SHOWN, ShardsCharge.shownAt(SHARDS, 1f));
        }
    }

    @Nested
    class WorldBlobScale {

        @Test
        void theGooInTheHandSwellsWithTheCharge() {
            assertEquals(ShardsCharge.BLOB_SMALLEST, ShardsCharge.blobScaleAt(0f), TOLERANCE);
            assertTrue(ShardsCharge.blobScaleAt(0.5f) > ShardsCharge.blobScaleAt(0.25f));
            assertEquals(ShardsCharge.BLOB_FULLEST, ShardsCharge.blobScaleAt(1f), TOLERANCE);
            assertEquals(ShardsCharge.BLOB_FULLEST, ShardsCharge.blobScaleAt(2f), TOLERANCE);
        }
    }

    @Nested
    class PalmBlobScale {

        @Test
        void thePalmBlobRestsAtItsOwnSizeWithNoCharge() {
            assertEquals(ShardsCharge.PALM_REST, ShardsCharge.palmScaleAt(0f), TOLERANCE);
        }

        @Test
        void thePalmBlobSitsMidwayAtHalfACharge() {
            assertEquals((ShardsCharge.PALM_REST + ShardsCharge.PALM_FULLEST) / 2f,
                    ShardsCharge.palmScaleAt(0.5f), TOLERANCE);
        }

        @Test
        void thePalmBlobSwellsToItsFullestAtFullChargeAndNoFurther() {
            assertEquals(ShardsCharge.PALM_FULLEST, ShardsCharge.palmScaleAt(1f), TOLERANCE);
            assertEquals(ShardsCharge.PALM_FULLEST, ShardsCharge.palmScaleAt(2f), TOLERANCE);
        }
    }

    @Nested
    class PalmShare {

        @Test
        void noSlingingChargeHeldShowsNoShare() {
            assertEquals(0f, ShardsCharge.palmShareOf(null, true, 0.7f), TOLERANCE);
        }

        @Test
        void anArmNotWoundUpShowsNoShare() {
            assertEquals(0f, ShardsCharge.palmShareOf(SHARDS, false, 0.7f), TOLERANCE);
        }

        @Test
        void aWoundUpSlingingChargeShowsTheShareItReached() {
            assertEquals(0.7f, ShardsCharge.palmShareOf(SHARDS, true, 0.7f), TOLERANCE);
            assertEquals(1f, ShardsCharge.palmShareOf(SHARDS, true, 1.5f), TOLERANCE);
        }
    }
}
