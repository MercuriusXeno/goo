package com.mercuriusxeno.goo.data;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GooFraction holds an exact reduced fraction with a positive denominator, and floors
 * and ceils toward the integer below and above it
 * (decision values-are-fractions-melt-floors-fabricate-ceils).
 */
class GooFractionTest {

    @Test
    void equalFractionsReduceToOneForm() {
        assertEquals(GooFraction.of(3, 8), GooFraction.of(6, 16));
        assertEquals(GooFraction.of(-1, 2), GooFraction.of(1, -2));
    }

    @Test
    void aNegativeFractionFloorsAwayFromZeroAndCeilsToward() {
        GooFraction negative = GooFraction.of(-7, 2);

        assertEquals(-4, negative.floor());
        assertEquals(-3, negative.ceiling());
    }

    @Test
    void sumAndProductStayExact() {
        GooFraction sum = GooFraction.of(1, 3).plus(GooFraction.of(1, 6));

        assertEquals(GooFraction.of(1, 2), sum);
        assertEquals(GooFraction.of(64, 1), GooFraction.of(64, 3).times(3));
    }

    @Test
    void comparingOrdersByMagnitude() {
        assertTrue(GooFraction.of(64, 3).compareTo(GooFraction.of(22, 1)) < 0);
        assertTrue(GooFraction.of(64, 3).compareTo(GooFraction.of(21, 1)) > 0);
    }

    @Test
    void aZeroDenominatorIsRefused() {
        assertThrows(ArithmeticException.class, () -> GooFraction.of(1, 0));
    }

    @Test
    void aWholeFractionPrintsAsAnInteger() {
        assertEquals("21", GooFraction.of(42, 2).toString());
        assertEquals("64/3", GooFraction.of(64, 3).toString());
    }
}
