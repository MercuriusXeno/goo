package com.mercuriusxeno.goo.ability.zoo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers what Kin tells each mob in reach: a passive mob beyond the short
 * distance is led to the player and one inside it holds, while a hostile mob,
 * a rallied mob and a mob beyond the radius are left alone.
 * leader-leads-passive-mobs-at-a-short-distance
 * rallied-mob-ai-trumps-leader
 */
class KinFollowTest {

    private static final Kin KIN = new Kin(12, 4);
    private static final boolean PEACEFUL = true;
    private static final boolean HOSTILE = false;
    private static final boolean RALLIED = true;
    private static final boolean UNRALLIED = false;

    private static double squared(double blocks) {
        return blocks * blocks;
    }

    @Test
    void passiveMobBeyondTheShortDistanceIsLed() {
        assertEquals(KinFollow.Move.LEAD, KinFollow.choose(PEACEFUL, UNRALLIED, squared(8), KIN));
    }

    @Test
    void passiveMobInsideTheShortDistanceHolds() {
        assertEquals(KinFollow.Move.HOLD, KinFollow.choose(PEACEFUL, UNRALLIED, squared(3), KIN));
    }

    @Test
    void hostileMobIsLeftAlone() {
        assertEquals(KinFollow.Move.LEAVE_ALONE, KinFollow.choose(HOSTILE, UNRALLIED, squared(8), KIN));
    }

    @Test
    void mobBeyondTheRadiusIsLeftAlone() {
        assertEquals(KinFollow.Move.LEAVE_ALONE, KinFollow.choose(PEACEFUL, UNRALLIED, squared(13), KIN));
    }

    @Test
    void ralliedMobIsLeftToRally() {
        assertEquals(KinFollow.Move.LEAVE_ALONE, KinFollow.choose(PEACEFUL, RALLIED, squared(8), KIN));
        assertEquals(KinFollow.Move.LEAVE_ALONE, KinFollow.choose(PEACEFUL, RALLIED, squared(3), KIN));
    }
}
