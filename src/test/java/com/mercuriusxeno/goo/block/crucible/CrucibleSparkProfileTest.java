package com.mercuriusxeno.goo.block.crucible;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that the crucible's sparks spawn at half the former chance and count and
 * move slower than the former ember profile (decision sparks-fewer-and-lower).
 * The former values are the ones origin/26.1 held at 94017a9.
 */
class CrucibleSparkProfileTest {

    private static final float FORMER_EMBER_CHANCE = 0.10f;
    private static final double FORMER_IGNITION_MIN_COUNT = 2;
    private static final double FORMER_BASE_SPEED = 0.05;
    private static final double FORMER_RANDOM_SPEED = 0.02;
    private static final double FORMER_BASE_FALL = -0.003;
    private static final double FORMER_RANDOM_FALL = 0.007;

    private static final CrucibleParticleHelper.SparkProfile PROFILE = CrucibleParticleHelper.EMBER_PROFILE;

    @Test
    void emberChanceIsAtMostHalfTheFormer() {
        assertTrue(CrucibleParticleHelper.EMBER_CHANCE_IDLE <= FORMER_EMBER_CHANCE / 2);
    }

    @Test
    void ignitionBurstIsAtMostHalfTheFormerCount() {
        assertTrue(CrucibleParticleHelper.IGNITION_SPARK_COUNT <= FORMER_IGNITION_MIN_COUNT / 2);
    }

    @Test
    void lateralSpeedIsBelowTheFormer() {
        assertTrue(PROFILE.baseSpeed() < FORMER_BASE_SPEED);
        assertTrue(PROFILE.baseSpeed() + PROFILE.randomSpeed() < FORMER_BASE_SPEED + FORMER_RANDOM_SPEED);
    }

    @Test
    void fallSpeedIsBelowTheFormer() {
        assertTrue(Math.abs(PROFILE.baseFall()) < Math.abs(FORMER_BASE_FALL));
        assertTrue(Math.abs(PROFILE.baseFall()) + PROFILE.randomFall()
                < Math.abs(FORMER_BASE_FALL) + FORMER_RANDOM_FALL);
    }
}
