package com.mercuriusxeno.goo.ability.zoo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers what a rallied mob does each tick: fights a foe in reach, else
 * walks to its rallier from beyond the near distance and holds within it.
 */
class RallyFollowTest {

    private static final double BEYOND = RallyFollow.NEAR_DISTANCE + 1.0;
    private static final double WITHIN = RallyFollow.NEAR_DISTANCE - 1.0;

    @Test
    void mobBeyondTheNearDistanceWalksToItsRallier() {
        assertEquals(RallyFollow.Move.FOLLOW, RallyFollow.choose(false, BEYOND * BEYOND));
    }

    @Test
    void mobWithinTheNearDistanceHolds() {
        assertEquals(RallyFollow.Move.HOLD, RallyFollow.choose(false, WITHIN * WITHIN));
    }

    @Test
    void mobWithAFoeInReachFightsWhereverItsRallierStands() {
        assertEquals(RallyFollow.Move.FIGHT, RallyFollow.choose(true, BEYOND * BEYOND));
        assertEquals(RallyFollow.Move.FIGHT, RallyFollow.choose(true, WITHIN * WITHIN));
    }
}
