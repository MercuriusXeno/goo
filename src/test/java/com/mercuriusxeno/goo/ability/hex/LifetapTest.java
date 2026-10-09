package com.mercuriusxeno.goo.ability.hex;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers the lifetap's leech and its spans: a hit heals by the fraction
 * while it stands and nothing after, a cast holds until ended, and a brew
 * stands for its duration, never shortening one already longer.
 */
class LifetapTest {

    private static final float FRACTION = 0.3f;
    private static final float DAMAGE = 4f;
    private static final long NOW = 1000L;
    private static final int BREW_TICKS = 72_000;
    private static final float TOLERANCE = 1e-6f;

    @Test
    void aHitHealsByTheFractionWhileItStands() {
        assertEquals(DAMAGE * FRACTION, Lifetap.hold(FRACTION).leechFor(DAMAGE, NOW), TOLERANCE);
    }

    @Test
    void aFadedLifetapHealsNothing() {
        assertEquals(0f, new Lifetap(FRACTION, NOW).leechFor(DAMAGE, NOW), TOLERANCE);
    }

    @Test
    void aBrewStandsForItsDuration() {
        assertEquals(NOW + BREW_TICKS, Lifetap.NONE.brew(FRACTION, BREW_TICKS, NOW).expiresAt());
    }

    @Test
    void aBrewKeepsALongerLifetap() {
        assertEquals(Lifetap.NEVER_EXPIRES, Lifetap.hold(FRACTION).brew(FRACTION, BREW_TICKS, NOW).expiresAt());
    }
}
