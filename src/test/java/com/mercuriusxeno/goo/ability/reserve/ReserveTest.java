package com.mercuriusxeno.goo.ability.reserve;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reserve drains health and hunger while held into reserve hearts and shanks
 * behind the bars, by the JSON's ratio, cap and floor; hits spend the hearts
 * before health and a dropping hunger bar spends the shanks (decisions
 * reserve-hearts-sit-behind-the-bar and reserve-channels-on-jelly).
 */
class ReserveTest {

    /** jelly_reserve.json: half a heart, and half a shank, drained every ten ticks, two banking one, ten at most, never under a half. */
    private static final ReserveDrain JELLY_RESERVE = new ReserveDrain(0.05f, 0.5f, 10f, 0.5f);
    private static final float FULL_HEALTH = 20f;
    private static final int FULL_FOOD = 20;
    private static final int CAP_HALVES = 20;
    private static final float SUM_DELTA = 1e-4f;
    private static final float DELTA = 1e-6f;

    private static ReserveDrain.Drawn held(Reserve standing, float health, int food, int ticks) {
        ReserveDrain.Drawn drawn = new ReserveDrain.Drawn(health, food, standing);
        for (int tick = 0; tick < ticks; tick++) {
            drawn = JELLY_RESERVE.draw(drawn.reserve(), drawn.health(), drawn.food());
        }
        return drawn;
    }

    private static Reserve banked(int hearts, int shanks, int lastFood) {
        return new Reserve(hearts, 0f, shanks, 0f, 0f, lastFood);
    }

    @Nested
    class Drain {

        @Test
        void eachHeldTickDrainsTheJsonAmountOfHealth() {
            ReserveDrain.Drawn drawn = held(Reserve.NONE, FULL_HEALTH, FULL_FOOD, 1);
            assertEquals(19.9f, drawn.health(), SUM_DELTA);
            assertTrue(drawn.reserve().stands());
            assertEquals(0, drawn.reserve().heartHalves());
        }

        @Test
        void twoHeartsDrainedBankOneReserveHeart() {
            ReserveDrain.Drawn drawn = held(Reserve.NONE, FULL_HEALTH, FULL_FOOD, 40);
            assertEquals(16f, drawn.health(), SUM_DELTA);
            assertEquals(Reserve.HALVES_PER_SLOT, drawn.reserve().heartHalves());
        }

        @Test
        void hungerDrainsAPointEachTimeTheDebtCoversOneAndBanksShanks() {
            ReserveDrain.Drawn drawn = held(Reserve.NONE, FULL_HEALTH, FULL_FOOD, 40);
            assertEquals(FULL_FOOD - 4, drawn.food());
            assertEquals(Reserve.HALVES_PER_SLOT, drawn.reserve().shankHalves());
            assertEquals(drawn.food(), drawn.reserve().lastFood());
        }

        @Test
        void theDrainStopsAtTheFloor() {
            ReserveDrain.Drawn drawn = held(Reserve.NONE, 1.05f, 2, 25);
            assertEquals(1f, drawn.health(), SUM_DELTA);
            assertEquals(1, drawn.food());
            ReserveDrain.Drawn atFloor = JELLY_RESERVE.draw(drawn.reserve(), drawn.health(), drawn.food());
            assertEquals(1f, atFloor.health(), SUM_DELTA);
            assertEquals(1, atFloor.food());
        }

        @Test
        void aReserveAtTheCapTakesNoMore() {
            Reserve capped = banked(CAP_HALVES, CAP_HALVES, FULL_FOOD);
            ReserveDrain.Drawn drawn = held(capped, FULL_HEALTH, FULL_FOOD, 40);
            assertEquals(FULL_HEALTH, drawn.health(), DELTA);
            assertEquals(FULL_FOOD, drawn.food());
            assertEquals(CAP_HALVES, drawn.reserve().heartHalves());
        }

        @Test
        void bankingStopsAtTheCapAndDropsTheCarry() {
            Reserve topped = banked(CAP_HALVES - 1, 0, FULL_FOOD).bankHealth(10f, 0.5f, CAP_HALVES);
            assertEquals(CAP_HALVES, topped.heartHalves());
            assertEquals(0f, topped.heartCarry(), DELTA);
        }
    }

    @Nested
    class Spend {

        @Test
        void hitsSpendTheReserveAHalfAPointBeforeHealth() {
            Reserve.Spent spent = banked(4, 0, FULL_FOOD).spend(3f);
            assertEquals(0f, spent.remainder(), DELTA);
            assertEquals(1, spent.reserve().heartHalves());
        }

        @Test
        void aHitPastTheReserveEndsItAndTheRestReachesHealth() {
            Reserve.Spent spent = banked(4, 0, FULL_FOOD).spend(6f);
            assertEquals(2f, spent.remainder(), DELTA);
            assertFalse(spent.reserve().stands());
        }

        @Test
        void aHitWithNoReserveHeartsPassesWhole() {
            Reserve shanksOnly = banked(0, 4, FULL_FOOD);
            assertSame(shanksOnly, shanksOnly.spend(3f).reserve());
        }

        @Test
        void hungerLostRefillsFromTheShanksUpToWhatStands() {
            Reserve reserve = banked(0, 3, FULL_FOOD);
            assertEquals(2, reserve.refillFor(FULL_FOOD - 2));
            assertEquals(3, reserve.refillFor(FULL_FOOD - 5));
            assertEquals(0, reserve.refillFor(FULL_FOOD));
            assertEquals(1, reserve.read(2, FULL_FOOD).shankHalves());
        }

        @Test
        void aRowFillsFromItsFirstSlot() {
            assertEquals(2, Reserve.halvesAt(3, 0));
            assertEquals(1, Reserve.halvesAt(3, 1));
            assertEquals(0, Reserve.halvesAt(3, 2));
        }
    }
}
