package com.mercuriusxeno.goo.client.hud;

import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pneuma's reserve row: each slot behind the air bar holds one vanilla
 * bubble's worth of air, the slot being spent draws popping, and slots past
 * the reserve draw nothing (decision pneuma-overfills-the-air-meter-with-a-brief-airless-immunity).
 */
class PneumaAirHudTest {

    /** Vanilla's air per bubble: a full meter of 300 ticks over ten bubbles. */
    private static final int BUBBLE_TICKS = 30;

    @Test
    void aSlotTheReserveFillsDrawsAWholeReserveBubble() {
        assertEquals(Optional.of(PneumaAirHud.RESERVE_FULL), PneumaAirHud.reserveSprite(2 * BUBBLE_TICKS, 1,
                BUBBLE_TICKS));
    }

    @Test
    void theSlotBeingSpentDrawsPopping() {
        assertEquals(Optional.of(PneumaAirHud.RESERVE_POPPING), PneumaAirHud.reserveSprite(BUBBLE_TICKS + 1, 1,
                BUBBLE_TICKS));
    }

    @Test
    void aSlotPastTheReserveDrawsNothing() {
        assertEquals(Optional.empty(), PneumaAirHud.reserveSprite(BUBBLE_TICKS, 1, BUBBLE_TICKS));
    }

    @Test
    void aFullReserveFillsTheRowOfTenAndNoMore() {
        assertEquals(10, PneumaAirHud.reserveSlots(10 * BUBBLE_TICKS, BUBBLE_TICKS));
        assertEquals(10, PneumaAirHud.reserveSlots(10 * BUBBLE_TICKS + 5, BUBBLE_TICKS));
        assertEquals(1, PneumaAirHud.reserveSlots(1, BUBBLE_TICKS));
    }
}
