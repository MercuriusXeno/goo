package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers how the client reads an agitator's attempt off its synced
 * countdown: the countdown only climbs when an attempt restarts it.
 */
class AgitatorWispsTest {

    @Test
    void aClimbingCountdownMarksAnAttempt() {
        assertTrue(AgitatorWisps.attempted(1, 300));
    }

    @Test
    void aFallingCountdownMarksNone() {
        assertFalse(AgitatorWisps.attempted(300, 299));
    }
}
