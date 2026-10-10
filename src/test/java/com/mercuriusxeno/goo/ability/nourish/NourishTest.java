package com.mercuriusxeno.goo.ability.nourish;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The nourishment clock: a point every interval up to and on the expiry,
 * a repeat invoke stacking its duration, and nothing past the expiry
 * (decision nourish-restores-hunger-over-time).
 */
class NourishTest {

    private static final long NOW = 1_000L;
    private static final int INTERVAL = 80;
    private static final int DURATION = 400;

    private static Nourish nourished() {
        return Nourish.NONE.apply(INTERVAL, DURATION, NOW);
    }

    /** Counts the points landing from now through the given tick, ticking the state the way the player tick does. */
    private static int pointsThrough(Nourish start, long last) {
        Nourish state = start;
        int points = 0;
        for (long tick = NOW; tick <= last; tick++) {
            if (state.feeds(tick)) {
                points++;
            }
            state = state.tick(tick);
        }
        return points;
    }

    @Nested
    class Apply {

        @Test
        void theFirstPointLandsAnIntervalFromNow() {
            Nourish nourish = nourished();
            assertEquals(NOW + INTERVAL, nourish.nextAt());
            assertEquals(NOW + DURATION, nourish.expiresAt());
        }

        @Test
        void aRepeatInvokeStacksTheDurationAndKeepsTheClock() {
            Nourish stacked = nourished().apply(INTERVAL, DURATION, NOW + 10);
            assertEquals(NOW + INTERVAL, stacked.nextAt());
            assertEquals(NOW + 2L * DURATION, stacked.expiresAt());
        }

        @Test
        void aHeldStartNeverExpiresAndFeedsEveryInterval() {
            // self-effects-trickle-until-ended
            Nourish held = Nourish.NONE.hold(INTERVAL, NOW);
            assertEquals(Nourish.NEVER_EXPIRES, held.expiresAt());
            assertEquals(NOW + INTERVAL, held.nextAt());
            assertEquals(10, pointsThrough(held, NOW + 10L * INTERVAL));
        }

        @Test
        void aBrewOverAHeldNourishmentKeepsItHeld() {
            Nourish held = Nourish.NONE.hold(INTERVAL, NOW);
            assertEquals(Nourish.NEVER_EXPIRES, held.apply(INTERVAL, DURATION, NOW + 1).expiresAt());
        }
    }

    @Nested
    class Tick {

        @Test
        void aPointLandsEveryIntervalForTheDuration() {
            assertEquals(DURATION / INTERVAL, pointsThrough(nourished(), NOW + DURATION + INTERVAL));
        }

        @Test
        void noPointLandsBeforeItsTime() {
            assertFalse(nourished().feeds(NOW + INTERVAL - 1));
            assertTrue(nourished().feeds(NOW + INTERVAL));
        }

        @Test
        void theNourishmentEndsPastItsExpiry() {
            Nourish last = new Nourish(INTERVAL, NOW + DURATION + INTERVAL, NOW + DURATION);
            assertFalse(last.feeds(NOW + DURATION + INTERVAL));
            assertSame(Nourish.NONE, last.tick(NOW + DURATION));
        }

        @Test
        void nothingStandingStaysPut() {
            assertSame(Nourish.NONE, Nourish.NONE.tick(NOW));
            assertFalse(Nourish.NONE.feeds(NOW));
        }
    }
}
