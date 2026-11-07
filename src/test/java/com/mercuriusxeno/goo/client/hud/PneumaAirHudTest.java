package com.mercuriusxeno.goo.client.hud;

import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pneuma's reserve row pops as vanilla's air bubbles do: each slot behind the
 * air bar holds one vanilla bubble's worth of air, draws whole until its last
 * two ticks, pops through them, then draws a small empty bubble, across as
 * many slots as the reserve's cap holds (decision
 * pneuma-overfills-the-air-meter-with-a-brief-airless-immunity).
 */
class PneumaAirHudTest {

    /** Vanilla's air per bubble: a full meter of 300 ticks over ten bubbles. */
    private static final int BUBBLE_TICKS = 30;

    @Test
    void aSlotWithMoreThanItsLastTwoTicksDrawsWhole() {
        assertEquals(Optional.of(PneumaAirHud.RESERVE_FULL), PneumaAirHud.reserveSprite(BUBBLE_TICKS + 3, 1,
                BUBBLE_TICKS));
    }

    @Test
    void aSlotPopsThroughItsLastTwoTicks() {
        assertEquals(Optional.of(PneumaAirHud.RESERVE_POPPING), PneumaAirHud.reserveSprite(BUBBLE_TICKS + 2, 1,
                BUBBLE_TICKS));
        assertEquals(Optional.of(PneumaAirHud.RESERVE_POPPING), PneumaAirHud.reserveSprite(BUBBLE_TICKS + 1, 1,
                BUBBLE_TICKS));
    }

    @Test
    void aSpentSlotDrawsASmallEmptyBubble() {
        assertEquals(Optional.of(PneumaAirHud.RESERVE_EMPTY), PneumaAirHud.reserveSprite(BUBBLE_TICKS, 1,
                BUBBLE_TICKS));
        assertEquals(Optional.of(PneumaAirHud.RESERVE_EMPTY), PneumaAirHud.reserveSprite(0, 0, BUBBLE_TICKS));
    }

    @Test
    void theRowHoldsAsManySlotsAsTheCapAndNoMoreThanTen() {
        assertEquals(10, PneumaAirHud.reserveSlots(10 * BUBBLE_TICKS, BUBBLE_TICKS));
        assertEquals(10, PneumaAirHud.reserveSlots(20 * BUBBLE_TICKS, BUBBLE_TICKS));
        assertEquals(5, PneumaAirHud.reserveSlots(5 * BUBBLE_TICKS, BUBBLE_TICKS));
    }
}
