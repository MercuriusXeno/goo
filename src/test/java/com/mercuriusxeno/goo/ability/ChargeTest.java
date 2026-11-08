package com.mercuriusxeno.goo.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Knives' shipped charge ramps its knife count and cone linearly from its
 * no-charge floors to its full charge (decisions shards-sling-then-morph-to-flechettes,
 * knives-cone-five-to-forty-five-and-three-to-twenty-seven-knives).
 */
class ChargeTest {

    private static final double TOLERANCE = 1e-9;
    private final Charge knives = AbilityJson.decode("crystal_knives").delivery().charge();

    @Test
    void aFullChargeThrowsTwentySevenKnivesAcrossFortyFiveDegrees() {
        assertEquals(27, knives.fleckCount(1f));
        assertEquals(45, knives.spreadDegrees(1f), TOLERANCE);
    }

    @Test
    void aHalfChargeThrowsFifteenKnivesAcrossTwentyFiveDegrees() {
        assertEquals(15, knives.fleckCount(0.5f));
        assertEquals(25, knives.spreadDegrees(0.5f), TOLERANCE);
    }

    @Test
    void anUnchargedReleaseThrowsThreeKnivesAcrossFiveDegrees() {
        assertEquals(3, knives.fleckCount(0f));
        assertEquals(5, knives.spreadDegrees(0f), TOLERANCE);
    }

    @Test
    void onlyAChargeNamingFlecksSlings() {
        assertTrue(knives.slings());
        assertFalse(Charge.of(30).slings());
        assertEquals(Charge.NONE, Charge.of(0));
    }
}
