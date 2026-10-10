package com.mercuriusxeno.goo.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A slinging charge's fleck count and spread follow the share of the charge
 * a hold reached, and its flecks leave across the sweep's ticks
 * (decision shards-sling-then-morph-to-flechettes).
 */
class ChargeTest {

    private static final double TOLERANCE = 1e-9;
    private final Charge shards = new Charge(30, 12, 60, 4);

    @Test
    void aFullChargeSlingsEveryFleckAcrossTheWholeCone() {
        assertEquals(12, shards.fleckCount(1f));
        assertEquals(60, shards.spreadDegrees(1f), TOLERANCE);
    }

    @Test
    void aHalfChargeSlingsHalfTheFlecksAcrossHalfTheCone() {
        assertEquals(6, shards.fleckCount(0.5f));
        assertEquals(30, shards.spreadDegrees(0.5f), TOLERANCE);
    }

    @Test
    void anUnchargedReleaseStillSlingsOneFleckStraight() {
        assertEquals(1, shards.fleckCount(0f));
        assertEquals(0, shards.spreadDegrees(0f), TOLERANCE);
    }

    @Test
    void theSweepLeavesFirstToLastAcrossItsTicks() {
        assertEquals(0, shards.launchDelay(0, 12));
        assertEquals(2, shards.launchDelay(6, 12));
        assertEquals(4, shards.launchDelay(11, 12));
        assertEquals(0, shards.launchDelay(0, 1));
    }

    @Test
    void onlyAChargeNamingFlecksSlings() {
        assertTrue(shards.slings());
        assertFalse(Charge.of(30).slings());
        assertEquals(Charge.NONE, Charge.of(0));
    }
}
