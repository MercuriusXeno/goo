package com.mercuriusxeno.goo.ability.gluttony;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The gluttony clock and banks: a point every interval up to and on the
 * expiry, a repeat invoke stacking its duration, a full bar banking the
 * point up to its cap, and banked hunger refilling a dropped bar
 * (decision gluttony-overheals-and-overhungers).
 */
class GluttonyTest {

    private static final long NOW = 1_000L;
    private static final int INTERVAL = 80;
    private static final int DURATION = 400;
    private static final int MAX_OVERHEAL = 4;
    private static final int MAX_OVERHUNGER = 3;
    private static final Gluttony CAPS = Gluttony.caps(INTERVAL, MAX_OVERHEAL, MAX_OVERHUNGER);

    private static Gluttony glutton() {
        return Gluttony.NONE.apply(CAPS, DURATION, NOW);
    }

    /** Counts the points landing from now through the given tick, ticking the state the way the player tick does. */
    private static int pointsThrough(Gluttony start, long last) {
        Gluttony state = start;
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
            Gluttony gluttony = glutton();
            assertEquals(NOW + INTERVAL, gluttony.nextAt());
            assertEquals(NOW + DURATION, gluttony.expiresAt());
            assertEquals(MAX_OVERHEAL, gluttony.maxOverheal());
            assertEquals(MAX_OVERHUNGER, gluttony.maxOverhunger());
        }

        @Test
        void aRepeatInvokeStacksTheDurationAndKeepsTheClockAndBanks() {
            Gluttony banked = glutton().land(true, true).after();
            Gluttony stacked = banked.apply(CAPS, DURATION, NOW + 10);
            assertEquals(NOW + INTERVAL, stacked.nextAt());
            assertEquals(NOW + 2L * DURATION, stacked.expiresAt());
            assertEquals(1, stacked.overhunger());
        }

        @Test
        void aHeldStartNeverExpiresAndFeedsEveryInterval() {
            // self-effects-trickle-until-ended
            Gluttony held = Gluttony.NONE.hold(CAPS, NOW);
            assertEquals(Gluttony.NEVER_EXPIRES, held.expiresAt());
            assertEquals(10, pointsThrough(held, NOW + 10L * INTERVAL));
        }

        @Test
        void aBrewOverAHeldGluttonyKeepsItHeld() {
            Gluttony held = Gluttony.NONE.hold(CAPS, NOW);
            assertEquals(Gluttony.NEVER_EXPIRES, held.apply(CAPS, DURATION, NOW + 1).expiresAt());
        }
    }

    @Nested
    class Tick {

        @Test
        void aPointLandsEveryIntervalForTheDuration() {
            assertEquals(DURATION / INTERVAL, pointsThrough(glutton(), NOW + DURATION + INTERVAL));
        }

        @Test
        void noPointLandsBeforeItsTime() {
            assertFalse(glutton().feeds(NOW + INTERVAL - 1));
            assertTrue(glutton().feeds(NOW + INTERVAL));
        }

        @Test
        void theGluttonyEndsPastItsExpiry() {
            Gluttony last = new Gluttony(INTERVAL, NOW + DURATION + INTERVAL, NOW + DURATION, MAX_OVERHEAL,
                    MAX_OVERHUNGER, 2f, 1);
            assertFalse(last.feeds(NOW + DURATION + INTERVAL));
            assertSame(Gluttony.NONE, last.tick(NOW + DURATION));
        }

        @Test
        void nothingStandingStaysPut() {
            assertSame(Gluttony.NONE, Gluttony.NONE.tick(NOW));
            assertFalse(Gluttony.NONE.feeds(NOW));
        }
    }

    @Nested
    class Land {

        @Test
        void barsShortOfFullFillAndBankNothing() {
            Gluttony.Landing landing = glutton().land(false, false);
            assertTrue(landing.eats());
            assertTrue(landing.heals());
            assertEquals(0, landing.after().overhunger());
            assertEquals(0f, landing.after().overheal());
        }

        @Test
        void fullBarsBankThePointPastTheirCaps() {
            Gluttony.Landing landing = glutton().land(true, true);
            assertFalse(landing.eats());
            assertFalse(landing.heals());
            assertEquals(1, landing.after().overhunger());
            assertEquals(Gluttony.HEALTH_PER_POINT, landing.after().overheal());
        }

        @Test
        void theBanksStopAtTheCapsTheJsonNames() {
            Gluttony state = glutton();
            for (int point = 0; point < MAX_OVERHEAL + MAX_OVERHUNGER; point++) {
                state = state.land(true, true).after();
            }
            assertEquals(MAX_OVERHUNGER, state.overhunger());
            assertEquals(MAX_OVERHEAL, state.overheal());
        }
    }

    @Nested
    class Banks {

        @Test
        void bankedHungerRefillsADroppedBarOnly() {
            Gluttony banked = glutton().land(true, false).after();
            assertFalse(banked.refills(true));
            assertTrue(banked.refills(false));
            assertEquals(0, banked.refilled().overhunger());
            assertFalse(glutton().refills(false));
        }

        @Test
        void damageSpendsTheOwnedOverhealDownToWhatStands() {
            Gluttony banked = glutton().land(false, true).after().land(false, true).after();
            assertEquals(1f, banked.spentTo(1f).overheal());
            assertSame(banked, banked.spentTo(5f));
        }
    }
}
