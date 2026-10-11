package com.mercuriusxeno.goo.ability.weird;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A wobbled mob's withheld hit knocks back in step with the damage it would
 * have dealt, and the wobble stands until its expiry tick and not past it.
 * weird-bounces-and-softens-harm
 */
class WobbleTest {

    private static final float DAMAGE = 3f;
    private static final long EXPIRES_AT = 1000L;
    private static final double TOLERANCE = 1e-9;

    @Test
    void knockbackScalesLinearlyWithTheWithheldDamage() {
        assertEquals(2 * WobbleEvents.knockbackFor(DAMAGE), WobbleEvents.knockbackFor(2 * DAMAGE), TOLERANCE);
        assertEquals(0, WobbleEvents.knockbackFor(0), TOLERANCE);
        assertTrue(WobbleEvents.knockbackFor(DAMAGE) > 0);
    }

    @Test
    void theWobbleStandsUntilItsExpiryTick() {
        Wobbled wobbled = new Wobbled(EXPIRES_AT);

        assertTrue(wobbled.standsAt(EXPIRES_AT - 1));
        assertFalse(wobbled.standsAt(EXPIRES_AT));
    }
}
