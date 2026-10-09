package com.mercuriusxeno.goo.ability.held;

import com.mercuriusxeno.goo.ability.program.Lux;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lux's standing and its night vision top-up: held Lux stands until its
 * effect ends, a brew's for its duration, and night vision is topped up
 * before it would flicker.
 * decision lux-night-vision-without-particles
 */
class LuxEventsTest {

    private static final long NOW = 1_000L;
    private static final int HOUR = 72_000;

    @Test
    void heldLuxStandsUntilItsEffectEnds() {
        assertTrue(Lux.NONE.hold().standsAt(Long.MAX_VALUE - 1));
        assertFalse(Lux.NONE.standsAt(NOW));
    }

    @Test
    void aBrewStandsForItsDurationAndNeverShortensWhatStands() {
        Lux brewed = Lux.NONE.brew(HOUR, NOW);
        assertEquals(NOW + HOUR, brewed.expiresAt());
        assertEquals(Lux.NEVER_EXPIRES, Lux.NONE.hold().brew(HOUR, NOW).expiresAt());
    }

    @Test
    void nightVisionIsToppedUpBeforeItFlickers() {
        assertTrue(LuxEvents.needsTopUp(0));
        assertTrue(LuxEvents.needsTopUp(LuxEvents.TOP_UP_BELOW - 1));
        assertFalse(LuxEvents.needsTopUp(LuxEvents.TOP_UP_BELOW));
    }
}
