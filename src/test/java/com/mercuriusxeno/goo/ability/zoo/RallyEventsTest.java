package com.mercuriusxeno.goo.ability.zoo;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** When a held rally re-runs around its drinker (decision zoo-rally-holds-for-the-brew). */
class RallyEventsTest {

    private static final long STARTED_AT = 1000L;

    @Test
    void theEatTickRunsNoRefreshSinceTheEatRanTheRally() {
        assertFalse(RallyEvents.refreshesOn(STARTED_AT, STARTED_AT));
    }

    @Test
    void theRallyReRunsEveryRefreshInterval() {
        assertTrue(RallyEvents.refreshesOn(STARTED_AT, STARTED_AT + RallyEvents.REFRESH_TICKS));
        assertTrue(RallyEvents.refreshesOn(STARTED_AT, STARTED_AT + 2L * RallyEvents.REFRESH_TICKS));
    }

    @Test
    void theRallyWaitsBetweenRefreshes() {
        assertFalse(RallyEvents.refreshesOn(STARTED_AT, STARTED_AT + RallyEvents.REFRESH_TICKS - 1));
        assertFalse(RallyEvents.refreshesOn(STARTED_AT, STARTED_AT + RallyEvents.REFRESH_TICKS + 1));
    }
}
