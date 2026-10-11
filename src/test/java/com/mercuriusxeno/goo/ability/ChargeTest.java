package com.mercuriusxeno.goo.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A slinging charge's knife count and cone follow the share of the charge
 * a hold reached (decision shards-sling-then-morph-to-flechettes).
 */
class ChargeTest {

    private static final double TOLERANCE = 1e-9;
    private final Charge shards = new Charge(30, 24, 60, 8);

    @Test
    void aFullChargeThrowsEveryKnifeAcrossTheWholeCone() {
        assertEquals(24, shards.fleckCount(1f));
        assertEquals(60, shards.spreadDegrees(1f), TOLERANCE);
    }

    @Test
    void aHalfChargeThrowsHalfTheKnivesAcrossThreeQuartersOfTheCone() {
        assertEquals(12, shards.fleckCount(0.5f));
        assertEquals(45, shards.spreadDegrees(0.5f), TOLERANCE);
    }

    @Test
    void anUnchargedReleaseStillThrowsOneKnifeAcrossHalfTheCone() {
        assertEquals(1, shards.fleckCount(0f));
        assertEquals(30, shards.spreadDegrees(0f), TOLERANCE);
    }

    @Test
    void onlyAChargeNamingFlecksSlings() {
        assertTrue(shards.slings());
        assertFalse(Charge.of(30).slings());
        assertEquals(Charge.NONE, Charge.of(0));
    }
}
