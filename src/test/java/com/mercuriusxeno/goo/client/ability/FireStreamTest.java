package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Flames' jet grows out from the glove over a quarter second, burns away from
 * the glove outward over the same quarter second after release
 * (decision flames-pixelated-fire-stream).
 */
class FireStreamTest {

    private static final double RANGE = 6;
    private static final double DELTA = 1e-9;

    @Test
    void frontTravelsTheRangeInAQuarterSecond() {
        assertEquals(0, FireStream.jetSpan(0, Double.NaN, RANGE).front(), DELTA);
        assertEquals(RANGE * 2 / 5, FireStream.jetSpan(2, Double.NaN, RANGE).front(), DELTA);
        assertEquals(RANGE, FireStream.jetSpan(5, Double.NaN, RANGE).front(), DELTA);
        assertEquals(RANGE, FireStream.jetSpan(40, Double.NaN, RANGE).front(), DELTA);
    }

    @Test
    void tailBurnsAwayFromTheGloveAfterRelease() {
        assertEquals(0, FireStream.jetSpan(40, Double.NaN, RANGE).tail(), DELTA);
        assertEquals(RANGE * 3 / 5, FireStream.jetSpan(40, 3, RANGE).tail(), DELTA);
        assertEquals(RANGE, FireStream.jetSpan(40, 5, RANGE).tail(), DELTA);
    }
}
