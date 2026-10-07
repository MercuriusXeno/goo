package com.mercuriusxeno.goo.client.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The half hearts the drain takes travel from the bar to the reserve row behind it, and nothing else travels (decision reserve-hearts-sit-behind-the-bar). */
class ReserveTravelsTest {

    private static final float NOW = 100f;
    private static final float DELTA = 1e-4f;

    @Nested
    class TravelsFor {

        @Test
        void eachHalfTheDrainTakesTravelsRightmostFirst() {
            List<ReserveTravels.Travel> travels = ReserveTravels.travelsFor(20, 18, true, 3, NOW);
            assertEquals(List.of(new ReserveTravels.Travel(9, 1, 1, NOW), new ReserveTravels.Travel(9, 0, 1, NOW)),
                    travels);
        }

        @Test
        void aHalfLandsInTheSlotTheNextBankedHalfFills() {
            assertEquals(2, ReserveTravels.travelsFor(15, 14, true, 4, NOW).getFirst().toSlot());
        }

        @Test
        void aDropWithNoDrainTravelsNowhere() {
            assertTrue(ReserveTravels.travelsFor(20, 16, false, 4, NOW).isEmpty());
        }

        @Test
        void healthGainedTravelsNowhere() {
            assertTrue(ReserveTravels.travelsFor(14, 16, true, 4, NOW).isEmpty());
        }
    }

    @Nested
    class Along {

        private final ReserveTravels.Point from = new ReserveTravels.Point(10f, 50f);
        private final ReserveTravels.Point to = new ReserveTravels.Point(30f, 40f);

        @Test
        void aHalfLeavesFromItsHealthSlot() {
            assertEquals(from, ReserveTravels.along(from, to, 0f));
        }

        @Test
        void aHalfArcsAboveTheLineAtMidFlight() {
            ReserveTravels.Point mid = ReserveTravels.along(from, to, 0.5f);
            assertEquals(20f, mid.x(), DELTA);
            assertEquals(45f - ReserveTravels.ARC_PIXELS, mid.y(), DELTA);
        }

        @Test
        void aHalfLandsOnItsReserveSlot() {
            ReserveTravels.Point landed = ReserveTravels.along(from, to, 1f);
            assertEquals(to.x(), landed.x(), DELTA);
            assertEquals(to.y(), landed.y(), DELTA);
        }
    }

    @Nested
    class Update {

        @Test
        void theFirstFrameStartsNoTravel() {
            assertTrue(new ReserveTravels().update(10, true, 0, NOW).isEmpty());
        }

        @Test
        void aDrainedHalfTravelsUntilItLands() {
            ReserveTravels travels = new ReserveTravels();
            travels.update(20, true, 0, NOW);
            assertEquals(1, travels.update(19, true, 0, NOW + 1).size());
            assertEquals(1, travels.update(19, true, 0, NOW + ReserveTravels.TRAVEL_TICKS).size());
            assertTrue(travels.update(19, true, 0, NOW + 1 + ReserveTravels.TRAVEL_TICKS).isEmpty());
        }
    }
}
