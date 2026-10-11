package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.Charge;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shards' charge: a chime at each step as the charge grows, capped at the
 * steps it chimes, and a goo blob in the hand swelling with the charge
 * (decision shards-sling-then-morph-to-flechettes).
 */
class ShardsChargeTest {

    private static final Charge SHARDS = new Charge(30, 24, 50, 8);
    private static final float TOLERANCE = 1e-5f;

    @Test
    void theChargeStepsUpAsItGrowsUpToTheStepsItChimes() {
        assertEquals(1, ShardsCharge.shownAt(SHARDS, 0f));
        assertEquals(5, ShardsCharge.shownAt(SHARDS, 0.2f));
        assertEquals(ShardsCharge.MOST_SHOWN, ShardsCharge.shownAt(SHARDS, 1f));
    }

    @Test
    void theGooInTheHandSwellsWithTheCharge() {
        assertEquals(ShardsCharge.BLOB_SMALLEST, ShardsCharge.blobScaleAt(0f), TOLERANCE);
        assertTrue(ShardsCharge.blobScaleAt(0.5f) > ShardsCharge.blobScaleAt(0.25f));
        assertEquals(ShardsCharge.BLOB_FULLEST, ShardsCharge.blobScaleAt(1f), TOLERANCE);
        assertEquals(ShardsCharge.BLOB_FULLEST, ShardsCharge.blobScaleAt(2f), TOLERANCE);
    }
}
