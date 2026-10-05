package com.mercuriusxeno.goo.client.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** Ember hearts shed sparks that rise and fade, flickering apart across slots, and only from standing ember halves (decision ember-hearts-shed-ember-particles). */
class EmberSparksTest {

    private static final int WINDOW = 400;
    private static final float DELTA = 1e-5f;

    private static int firstSpawn(int slot) {
        return IntStream.range(0, WINDOW).filter(tick -> EmberSparks.spawnsAt(slot, tick)).findFirst().orElseThrow();
    }

    @Test
    void anEmberSlotSpawnsASparkOnItsSpawnTick() {
        int spawn = firstSpawn(0);
        List<EmberSparks.Spark> sparks = EmberSparks.sparks(0, 2, spawn, 0f);
        assertFalse(sparks.isEmpty());
        assertTrue(sparks.stream().anyMatch(spark -> Math.abs(spark.alpha() - 1f) < DELTA));
    }

    @Test
    void anAshSlotShedsNothing() {
        assertTrue(IntStream.range(0, WINDOW).allMatch(tick -> EmberSparks.sparks(0, 0, tick, 0f).isEmpty()));
    }

    @Test
    void aSparkRisesAndFadesToNothingOverItsLife() {
        int spawn = firstSpawn(3);
        EmberSparks.Spark born = EmberSparks.sparks(3, 2, spawn, 0f).getLast();
        EmberSparks.Spark aged = EmberSparks.sparks(3, 2, spawn + EmberSparks.LIFE_TICKS / 2, 0f).stream()
                .filter(spark -> Math.abs(spark.alpha() - 0.5f) < DELTA).findFirst().orElseThrow();
        assertTrue(aged.y() < born.y());
        assertEquals(EmberSparks.RISE_PIXELS / 2, born.y() - aged.y(), DELTA);
        assertEquals(0f, EmberSparks.sparks(3, 2, spawn + EmberSparks.LIFE_TICKS - 1, 1f).stream()
                .mapToDouble(EmberSparks.Spark::alpha).min().orElseThrow(), DELTA);
    }

    @Test
    void slotsFlickerOnDifferingTicks() {
        List<Integer> first = IntStream.range(0, WINDOW).filter(tick -> EmberSparks.spawnsAt(0, tick)).boxed().toList();
        List<Integer> second = IntStream.range(0, WINDOW).filter(tick -> EmberSparks.spawnsAt(1, tick)).boxed().toList();
        assertNotEquals(first, second);
    }

    @Test
    void aHalfEmberShedsFromItsStandingHalf() {
        assertTrue(IntStream.range(0, WINDOW).boxed()
                .flatMap(tick -> EmberSparks.sparks(5, 1, tick, 0f).stream())
                .filter(spark -> Math.abs(spark.alpha() - 1f) < DELTA)
                .allMatch(spark -> spark.x() <= EmberSparks.HEART_WIDTH / 2));
    }

    @Test
    void opacityRidesTheAlphaChannel() {
        assertEquals(0x80FF9020, new EmberSparks.Spark(0f, 0f, 128f / 255f, 0xFFFF9020).argb());
    }
}
