package com.mercuriusxeno.goo.ability.world;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A time skip moves the clock forward only, and winds time since rest back
 * by the ticks skipped, never below zero
 * (decision timekeeper-prism-banks-ticks-forward-only).
 */
class TimeSkipTest {

    private static final long DAY_TIME = 6000;
    private static final long SKIP = 400;

    @Test
    void aSkipMovesTheClockForwardByItsTicks() {
        assertEquals(DAY_TIME + SKIP, TimeSkip.dayTimeAfter(DAY_TIME, SKIP));
    }

    @Test
    void aSkipBackwardIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> TimeSkip.dayTimeAfter(DAY_TIME, -1));
    }

    @Test
    void timeSinceRestWindsBackByTheSkip() {
        assertEquals(1000 - (int) SKIP, TimeSkip.restAfterSkip(1000, SKIP));
    }

    @Test
    void timeSinceRestStopsAtZero() {
        assertEquals(0, TimeSkip.restAfterSkip(100, SKIP));
    }
}
