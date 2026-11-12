package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The drawn wave leaves the glove on tick 0, reaches the landing on the delay
 * tick and moves along the line between at an even pace, for any distance
 * and delay (decision click-is-a-hit-scan-drawn-as-a-delayed-wave).
 */
class ScanWaveProgressTest {

    private static final Vec3 GLOVE = new Vec3(0.3, 65.2, -0.4);
    private static final double CLOSE = 1e-9;

    private static void assertVec(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, CLOSE, actual.toString());
        assertEquals(expected.y, actual.y, CLOSE, actual.toString());
        assertEquals(expected.z, actual.z, CLOSE, actual.toString());
    }

    @ParameterizedTest
    @CsvSource({"4, 2", "10, 5", "31.5, 16", "1, 1"})
    void startsAtTheGlove(double distance, int delay) {
        assertVec(GLOVE, ScanWaveProgress.at(GLOVE, GLOVE.add(0, 0, distance), delay, 0));
    }

    @ParameterizedTest
    @CsvSource({"4, 2", "10, 5", "31.5, 16", "1, 1"})
    void reachesTheLandingOnTheDelayTick(double distance, int delay) {
        Vec3 landing = GLOVE.add(distance, 0, 0);
        assertVec(landing, ScanWaveProgress.at(GLOVE, landing, delay, delay));
    }

    @ParameterizedTest
    @CsvSource({"4, 2", "10, 5", "31.5, 16"})
    void movesLinearlyBetween(double distance, int delay) {
        Vec3 landing = GLOVE.add(0, -distance, distance);
        float quarter = delay / 4f;
        assertVec(GLOVE.add(landing.subtract(GLOVE).scale(0.25)), ScanWaveProgress.at(GLOVE, landing, delay, quarter));
    }
}
