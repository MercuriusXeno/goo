package com.mercuriusxeno.goo.client.tartarus;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers how the rift's embers fade as the rift sinks away, so they end with it rather than all at once.
 */
class RiftShapeTest {

    private static final double TIME = 12.5;

    @Test
    void theEmbersDimAsTheRiftSinks() {
        double open = brightest(0);
        double half = brightest(0.5);
        double late = brightest(0.9);

        assertTrue(half < open, half + " dims below " + open);
        assertTrue(late < half / 2, late + " dims well below " + half);
    }

    @Test
    void theEmbersStillShowFaintlyAsTheRiftAlmostVanishes() {
        List<RiftShape.Ember> embers = embersAt(0.97);

        assertFalse(embers.isEmpty(), "embers still standing at 97 percent sunk");
        assertTrue(brightest(0.97) < 0.05, "and nearly faded: " + brightest(0.97));
    }

    private static double brightest(double close) {
        return embersAt(close).stream().mapToDouble(ember -> (ember.color() >>> 24) / 255.0).max().orElse(0);
    }

    private static List<RiftShape.Ember> embersAt(double close) {
        List<RiftShape.Ember> embers = new ArrayList<>();
        new RiftShape(1, close, 0, TIME).embers(embers::add);
        return embers;
    }
}
