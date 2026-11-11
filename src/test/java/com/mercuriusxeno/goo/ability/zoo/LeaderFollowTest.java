package com.mercuriusxeno.goo.ability.zoo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers what Leader tells each mob in reach: a passive mob beyond the short
 * distance is led to the player and one inside it holds, while a hostile mob,
 * a rallied mob and a mob beyond the radius are left alone.
 * leader-leads-passive-mobs-at-a-short-distance
 * rallied-mob-ai-trumps-leader
 */
class LeaderFollowTest {

    private static final Leader LEADER = new Leader(12, 4);
    private static final boolean PEACEFUL = true;
    private static final boolean HOSTILE = false;
    private static final boolean RALLIED = true;
    private static final boolean UNRALLIED = false;

    private static double squared(double blocks) {
        return blocks * blocks;
    }

    @Test
    void passiveMobBeyondTheShortDistanceIsLed() {
        assertEquals(LeaderFollow.Move.LEAD, LeaderFollow.choose(PEACEFUL, UNRALLIED, squared(8), LEADER));
    }

    @Test
    void passiveMobInsideTheShortDistanceHolds() {
        assertEquals(LeaderFollow.Move.HOLD, LeaderFollow.choose(PEACEFUL, UNRALLIED, squared(3), LEADER));
    }

    @Test
    void hostileMobIsLeftAlone() {
        assertEquals(LeaderFollow.Move.LEAVE_ALONE, LeaderFollow.choose(HOSTILE, UNRALLIED, squared(8), LEADER));
    }

    @Test
    void mobBeyondTheRadiusIsLeftAlone() {
        assertEquals(LeaderFollow.Move.LEAVE_ALONE, LeaderFollow.choose(PEACEFUL, UNRALLIED, squared(13), LEADER));
    }

    @Test
    void ralliedMobIsLeftToRally() {
        assertEquals(LeaderFollow.Move.LEAVE_ALONE, LeaderFollow.choose(PEACEFUL, RALLIED, squared(8), LEADER));
        assertEquals(LeaderFollow.Move.LEAVE_ALONE, LeaderFollow.choose(PEACEFUL, RALLIED, squared(3), LEADER));
    }
}
