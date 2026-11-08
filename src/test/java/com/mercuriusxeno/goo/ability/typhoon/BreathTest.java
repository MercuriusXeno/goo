package com.mercuriusxeno.goo.ability.typhoon;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pneuma's air state: a held tick refills the vanilla air and spills the rest
 * into a capped reserve, the reserve drains before the vanilla air while the
 * player cannot breathe, and the immunity window a held tick opens lets no
 * air deplete until it closes (decision pneuma-overfills-the-air-meter-with-a-brief-airless-immunity).
 */
class BreathTest {

    /** Vanilla's full air supply, in ticks. */
    private static final int MAX_AIR = 300;
    /** typhoon_pneuma.json's refill per held tick, reserve cap and immunity. */
    private static final int REFILL = 30;
    private static final int RESERVE_CAP = 300;
    private static final int IMMUNITY = 60;
    private static final long NOW = 1000L;
    /** One tick's breath underwater, as vanilla consumes it. */
    private static final int ONE_TICK = 1;

    @Nested
    class Refill {

        @Test
        void aHeldTickFillsTheVanillaAirFirst() {
            Breath.Refilled refilled = Breath.NONE.refilled(100, MAX_AIR, REFILL, RESERVE_CAP, IMMUNITY, NOW);

            assertEquals(100 + REFILL, refilled.air());
            assertEquals(0, refilled.breath().reserve());
        }

        @Test
        void airPastTheVanillaMeterSpillsIntoTheReserve() {
            Breath.Refilled refilled = Breath.NONE.refilled(290, MAX_AIR, REFILL, RESERVE_CAP, IMMUNITY, NOW);

            assertEquals(MAX_AIR, refilled.air());
            assertEquals(290 + REFILL - MAX_AIR, refilled.breath().reserve());
        }

        @Test
        void theReserveStopsAtItsCap() {
            Breath nearlyFull = new Breath(RESERVE_CAP - 10, RESERVE_CAP, 0L, 0);

            Breath.Refilled refilled = nearlyFull.refilled(MAX_AIR, MAX_AIR, REFILL, RESERVE_CAP, IMMUNITY, NOW);

            assertEquals(MAX_AIR, refilled.air());
            assertEquals(RESERVE_CAP, refilled.breath().reserve());
            assertEquals(RESERVE_CAP, refilled.breath().reserveCap());
        }

        @Test
        void aHeldTickOpensTheImmunityWindow() {
            Breath.Refilled refilled = Breath.NONE.refilled(MAX_AIR, MAX_AIR, REFILL, RESERVE_CAP, IMMUNITY, NOW);

            assertEquals(NOW + IMMUNITY, refilled.breath().immuneUntil());
        }
    }

    @Nested
    class Drain {

        @Test
        void theReserveDrainsBeforeTheVanillaAir() {
            Breath.Spent spent = new Breath(10, RESERVE_CAP, 0L, 0).spent(ONE_TICK, NOW);

            assertEquals(0, spent.fromAir());
            assertEquals(9, spent.breath().reserve());
        }

        @Test
        void whatTheReserveCannotCoverFallsToTheVanillaAir() {
            Breath.Spent spent = new Breath(1, RESERVE_CAP, 0L, 0).spent(3, NOW);

            assertEquals(2, spent.fromAir());
            assertEquals(0, spent.breath().reserve());
        }

        @Test
        void anEmptyReserveLeavesTheBreathToTheVanillaAir() {
            Breath.Spent spent = Breath.NONE.spent(ONE_TICK, NOW);

            assertEquals(ONE_TICK, spent.fromAir());
            assertEquals(0, spent.breath().reserve());
        }
    }

    @Nested
    class Immunity {

        @Test
        void noAirDepletesInsideTheWindow() {
            Breath.Spent spent = new Breath(10, RESERVE_CAP, NOW + 1 - IMMUNITY, IMMUNITY).spent(ONE_TICK, NOW);

            assertEquals(0, spent.fromAir());
            assertEquals(10, spent.breath().reserve());
        }

        @Test
        void airDepletesOnceTheWindowCloses() {
            Breath.Spent spent = new Breath(0, RESERVE_CAP, NOW - IMMUNITY, IMMUNITY).spent(ONE_TICK, NOW);

            assertEquals(ONE_TICK, spent.fromAir());
        }
    }
}
