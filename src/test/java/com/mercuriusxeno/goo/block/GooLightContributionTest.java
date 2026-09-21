package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.GooTypeDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests that a slot's light comes from the definition's peak and saturation:
 * peak * sqrt(min(1, fill / saturation)) rounded, floored at 4 while any goo
 * is present, and 0 for an empty slot.
 */
class GooLightContributionTest {

    private static final int FLOOR = 4;
    private static final long CAPACITY = 1000L;

    /**
     * Each row: peak, saturation, amount out of 1000, expected light. Expected
     * values are the curve worked by hand: blaze at half its saturation is
     * 15 * sqrt(0.5) = 10.6, rounded 11; at and past saturation the peak.
     */
    @ParameterizedTest
    @CsvSource({
        "15, 0.5, 250, 11",
        "15, 0.5, 500, 15",
        "15, 0.5, 1000, 15",
        "8, 0.6, 150, 4",
        "8, 0.6, 300, 6",
        "8, 0.6, 600, 8",
        "4, 0.5, 500, 4",
    })
    void followsSqrtCurveOfDefinition(int peak, float saturation, long amount, int expected) {
        GooTypeDefinition type = new GooTypeDefinition(peak, saturation);
        assertEquals(expected, GooLightContribution.forSlot(type, amount, CAPACITY));
    }

    /**
     * A trace of goo emits the floor even when the curve rounds below it,
     * and a type whose peak is 0 still emits the floor while present.
     */
    @Test
    void presentGooEmitsAtLeastTheFloor() {
        assertEquals(FLOOR, GooLightContribution.forSlot(new GooTypeDefinition(15, 0.5f), 1L, CAPACITY));
        assertEquals(FLOOR, GooLightContribution.forSlot(new GooTypeDefinition(0, 0.5f), CAPACITY, CAPACITY));
    }

    /**
     * No type, no amount or no capacity is no light.
     */
    @Test
    void emptySlotEmitsNothing() {
        GooTypeDefinition type = new GooTypeDefinition(15, 0.5f);
        assertEquals(0, GooLightContribution.forSlot(null, CAPACITY, CAPACITY));
        assertEquals(0, GooLightContribution.forSlot(type, 0L, CAPACITY));
        assertEquals(0, GooLightContribution.forSlot(type, CAPACITY, 0L));
    }

    /**
     * Two peaks sum but never pass the ceiling.
     */
    @Test
    void sumsClampToCeiling() {
        assertEquals(GooLightContribution.MAX_LIGHT, GooLightContribution.sumClamped(8, 8));
        assertEquals(12, GooLightContribution.addClamped(4, 8));
    }
}
