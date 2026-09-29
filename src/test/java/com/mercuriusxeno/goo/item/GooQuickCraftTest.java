package com.mercuriusxeno.goo.item;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for GooQuickCraft pure utility: charitable and greedy distribution math.
 * These tests do not require Minecraft class init - they only test pure logic.
 */
class GooQuickCraftTest {

    // -- charitablePerSlot --

    /**
     * Even split: 10,000 mB across 5 slots = 2,000 mB each.
     */
    @Test
    void charitable_evenSplit() {
        assertEquals(2000, GooQuickCraft.charitablePerSlot(10_000, 5));
    }

    /**
     * Uneven split floors: 7,777 mB across 3 slots = 2,592 mB each (1 mB remainder).
     */
    @Test
    void charitable_unevenFloors() {
        assertEquals(2592, GooQuickCraft.charitablePerSlot(7_777, 3));
    }

    /**
     * Single slot gets everything.
     */
    @Test
    void charitable_singleSlot() {
        assertEquals(5000, GooQuickCraft.charitablePerSlot(5000, 1));
    }

    /**
     * Zero slots returns zero (defensive).
     */
    @Test
    void charitable_zeroSlots() {
        assertEquals(0, GooQuickCraft.charitablePerSlot(5000, 0));
    }

    /**
     * Volume less than slot count: each slot gets zero.
     */
    @Test
    void charitable_volumeLessThanSlots() {
        assertEquals(0, GooQuickCraft.charitablePerSlot(2, 5));
    }

    /**
     * Large volume: 100,000 mB across 4 slots = 25,000 each.
     */
    @Test
    void charitable_largeVolume() {
        assertEquals(25_000, GooQuickCraft.charitablePerSlot(100_000, 4));
    }

    // -- greedyPerSlot (decision right-drag-over-a-thousand-places-thousands) --

    /**
     * Holding more than one goo, a right-drag places one goo per slot:
     * 5,000 mB and 1,001 mB both answer 1,000 mB.
     *
     * @param carriedVolume the volume on the cursor
     */
    @ParameterizedTest
    @ValueSource(ints = {5_000, 1_001})
    void greedyPlacesOneGooAboveOneGoo(int carriedVolume) {
        assertEquals(GooStacks.THOUSAND, GooQuickCraft.greedyPerSlot(carriedVolume));
    }

    /**
     * Holding one goo or less, a right-drag places one unit per slot:
     * 1,000 mB, 500 mB and 1 mB answer 1 mB.
     *
     * @param carriedVolume the volume on the cursor
     */
    @ParameterizedTest
    @ValueSource(ints = {1_000, 500, 1})
    void greedyPlacesOneUnitAtOneGooOrLess(int carriedVolume) {
        assertEquals(1, GooQuickCraft.greedyPerSlot(carriedVolume));
    }

    /**
     * The boundary sits above one goo: 1,001 mB answers 1,000 mB, 1,000 mB answers 1 mB.
     */
    @Test
    void greedyBoundarySitsAboveOneGoo() {
        assertEquals(1_000, GooQuickCraft.greedyPerSlot(1_001));
        assertEquals(1, GooQuickCraft.greedyPerSlot(1_000));
    }
}
