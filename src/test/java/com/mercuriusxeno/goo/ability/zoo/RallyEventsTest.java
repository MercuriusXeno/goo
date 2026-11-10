package com.mercuriusxeno.goo.ability.zoo;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When a held rally re-runs around its drinker (decision zoo-rally-holds-for-the-brew),
 * and when a rallied mob strikes (decision rallied-mobs-follow-the-player-who-rallied-them).
 */
class RallyEventsTest {

    private static final long STARTED_AT = 1000L;
    /** The strike cooldown the operator set, three quarters of a second. */
    private static final int STRIKE_COOLDOWN = 15;
    private static final int LONG_LIVED_MOB_TICKS = 4321;
    private static final int NEVER_STRUCK = 0;
    private static final int LAST_STRIKE = 500;

    @Test
    void theEatTickRunsNoRefreshSinceTheEatRanTheRally() {
        assertFalse(RallyEvents.refreshesOn(STARTED_AT, STARTED_AT));
    }

    @Test
    void theRallyReRunsEveryRefreshInterval() {
        assertTrue(RallyEvents.refreshesOn(STARTED_AT, STARTED_AT + RallyEvents.REFRESH_TICKS));
        assertTrue(RallyEvents.refreshesOn(STARTED_AT, STARTED_AT + 2L * RallyEvents.REFRESH_TICKS));
    }

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

    @Test
    void theRallyWaitsBetweenRefreshes() {
        assertFalse(RallyEvents.refreshesOn(STARTED_AT, STARTED_AT + RallyEvents.REFRESH_TICKS - 1));
        assertFalse(RallyEvents.refreshesOn(STARTED_AT, STARTED_AT + RallyEvents.REFRESH_TICKS + 1));
    }
}
