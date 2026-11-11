package com.mercuriusxeno.goo.ability.zoo;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When a rallied mob strikes (decision rallied-mobs-follow-the-player-who-rallied-them).
 */
class RallyEventsTest {

    /** The strike cooldown the operator set, three quarters of a second. */
    private static final int STRIKE_COOLDOWN = 15;
    private static final int LONG_LIVED_MOB_TICKS = 4321;
    private static final int NEVER_STRUCK = 0;
    private static final int LAST_STRIKE = 500;

    /** rallied-mobs-follow-the-player-who-rallied-them: the first strike lands on arrival, unaligned to any beat. */
    @Test
    void aMobThatHasNotStruckStrikesOnArrival() {
        assertTrue(RallyEvents.strikeReady(LONG_LIVED_MOB_TICKS, NEVER_STRUCK));
    }

    /** rallied-mobs-follow-the-player-who-rallied-them */
    @Test
    void aMobStrikesFifteenTicksAfterItsLastStrike() {
        assertFalse(RallyEvents.strikeReady(LAST_STRIKE + STRIKE_COOLDOWN - 1, LAST_STRIKE));
        assertTrue(RallyEvents.strikeReady(LAST_STRIKE + STRIKE_COOLDOWN, LAST_STRIKE));
    }
}
