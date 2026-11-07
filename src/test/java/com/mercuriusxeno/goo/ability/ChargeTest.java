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
    private final Charge knives = new Charge(30, 24, 60, 8);

    @Test
    void aFullChargeThrowsEveryKnifeAcrossTheWholeCone() {
        assertEquals(24, knives.fleckCount(1f));
        assertEquals(60, knives.spreadDegrees(1f), TOLERANCE);
    }

    @Test
    void aHalfChargeThrowsHalfTheKnivesAcrossThreeQuartersOfTheCone() {
        assertEquals(12, knives.fleckCount(0.5f));
        assertEquals(45, knives.spreadDegrees(0.5f), TOLERANCE);
    }

    @Test
    void anUnchargedReleaseStillThrowsOneKnifeAcrossHalfTheCone() {
        assertEquals(1, knives.fleckCount(0f));
        assertEquals(30, knives.spreadDegrees(0f), TOLERANCE);
    }

    @Test
    void onlyAChargeNamingFlecksSlings() {
        assertTrue(knives.slings());
        assertFalse(Charge.of(30).slings());
        assertEquals(Charge.NONE, Charge.of(0));
    }
}
