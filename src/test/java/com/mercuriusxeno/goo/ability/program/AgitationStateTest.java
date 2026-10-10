package com.mercuriusxeno.goo.ability.program;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the agitator's interval: a failed attempt shrinks it by the factor
 * down to the floor, a spawn returns it to the start, and the countdown
 * calls an attempt once it runs out.
 */
class AgitationStateTest {

    private static final int START = 400;
    private static final double SHRINK = 0.75;
    private static final int FLOOR = 40;

    @Test
    void aFailedAttemptShrinksTheInterval() {
        assertEquals(300, AgitationState.nextInterval(START, false, START, SHRINK, FLOOR));
    }

    @Test
    void theIntervalHoldsAtTheFloor() {
        assertEquals(FLOOR, AgitationState.nextInterval(45, false, START, SHRINK, FLOOR));
    }

    @Test
    void aSpawnResetsTheInterval() {
        assertEquals(START, AgitationState.nextInterval(FLOOR, true, START, SHRINK, FLOOR));
    }

    @Test
    void theCountdownCallsAnAttemptWhenItRunsOut() {
        AgitationState state = new AgitationState();
        state.startIfIdle(2);

        assertFalse(state.tickDown());
        assertTrue(state.tickDown());
    }

    @Test
    void aStartedAgitatorKeepsItsInterval() {
        AgitationState state = new AgitationState();
        state.startIfIdle(START);
        state.restart(FLOOR);
        state.startIfIdle(START);

        assertEquals(FLOOR, state.interval());
    }
}
