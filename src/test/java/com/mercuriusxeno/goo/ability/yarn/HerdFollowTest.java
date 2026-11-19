package com.mercuriusxeno.goo.ability.yarn;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers what Herd tells each mob in reach: a passive mob beyond the short
 * distance is led to the player and one inside it holds, while a hostile mob,
 * a led mob and a mob beyond the radius are left alone.
 * leader-leads-passive-mobs-at-a-short-distance
 * rallied-mob-ai-trumps-leader
 */
class HerdFollowTest {

    private static final Herd HERD = new Herd(12, 4);
    private static final boolean PEACEFUL = true;
    private static final boolean HOSTILE = false;
    private static final boolean LED = true;
    private static final boolean UNLED = false;

    private static double squared(double blocks) {
        return blocks * blocks;
    }

    @Test
    void passiveMobBeyondTheShortDistanceIsLed() {
        assertEquals(HerdFollow.Move.APPROACH, HerdFollow.choose(PEACEFUL, UNLED, squared(8), HERD));
    }

    @Test
    void passiveMobInsideTheShortDistanceHolds() {
        assertEquals(HerdFollow.Move.HOLD, HerdFollow.choose(PEACEFUL, UNLED, squared(3), HERD));
    }

    @Test
    void hostileMobIsLeftAlone() {
        assertEquals(HerdFollow.Move.LEAVE_ALONE, HerdFollow.choose(HOSTILE, UNLED, squared(8), HERD));
    }

    @Test
    void mobBeyondTheRadiusIsLeftAlone() {
        assertEquals(HerdFollow.Move.LEAVE_ALONE, HerdFollow.choose(PEACEFUL, UNLED, squared(13), HERD));
    }

    @Test
    void ledMobIsLeftToLead() {
        assertEquals(HerdFollow.Move.LEAVE_ALONE, HerdFollow.choose(PEACEFUL, LED, squared(8), HERD));
        assertEquals(HerdFollow.Move.LEAVE_ALONE, HerdFollow.choose(PEACEFUL, LED, squared(3), HERD));
    }
}
