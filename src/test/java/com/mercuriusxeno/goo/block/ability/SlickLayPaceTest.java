package com.mercuriusxeno.goo.block.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Slick throws its base drips a tick at rest and more by the caster's speed
 * over their walk, never fewer, a tick's share of a drip landing on a seeded
 * draw so a hold averages the scaled rate
 * (decision slick-resolves-faster-by-speed-over-unadjusted-speed).
 */
class SlickLayPaceTest {

    /** A player's base movement speed attribute. */
    private static final double WALKING_ATTRIBUTE = 0.1;
    private static final double WALK = SlickLayPace.unadjustedSpeed(WALKING_ATTRIBUTE);
    private static final int BASE_DRIPS = 2;
    /** Ticks of a long hold, enough to read the scaled rate's average. */
    private static final int LONG_HOLD = 4000;

    @Test
    void aWalkingAttributeWalksAPlayerAtVanillasSettledSpeed() {
        assertEquals(0.2159, WALK, 1e-4);
    }

    @Test
    void aCasterAtRestThrowsTheBaseDrips() {
        assertEquals(BASE_DRIPS, SlickLayPace.dripsThisTick(BASE_DRIPS, SlickLayPace.factor(0, WALK), 1));
    }

    @Test
    void aCasterAtTheRampsTopSpeedThrowsAboutNineDripsATick() {
        assertEquals(9.26, averageDrips(SlickLayPace.factor(1.0, WALK)), 0.1);
    }

    @Test
    void aSprintingCasterThrowsAboutTwoAndAHalfDripsATick() {
        assertEquals(2.6, averageDrips(SlickLayPace.factor(0.28, WALK)), 0.1);
    }

    private static double averageDrips(double factor) {
        long total = 0;
        for (int held = 1; held <= LONG_HOLD; held++) {
            total += SlickLayPace.dripsThisTick(BASE_DRIPS, factor, held);
        }
        return (double) total / LONG_HOLD;
    }
}
