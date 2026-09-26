package com.mercuriusxeno.goo.item;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for OmniblobQuickCraft pure utility: charitable and greedy distribution math.
 * These tests do not require Minecraft class init - they only test pure logic.
 */
class OmniblobQuickCraftTest {

    // -- charitablePerSlot --

    /**
     * Even split: 10,000 mB across 5 slots = 2,000 mB each.
     */
    @Test
    void charitable_evenSplit() {
        assertEquals(2000, OmniblobQuickCraft.charitablePerSlot(10_000, 5));
    }

    /**
     * Uneven split floors: 7,777 mB across 3 slots = 2,592 mB each (1 mB remainder).
     */
    @Test
    void charitable_unevenFloors() {
        assertEquals(2592, OmniblobQuickCraft.charitablePerSlot(7_777, 3));
    }

    /**
     * Single slot gets everything.
     */
    @Test
    void charitable_singleSlot() {
        assertEquals(5000, OmniblobQuickCraft.charitablePerSlot(5000, 1));
    }

    /**
     * Zero slots returns zero (defensive).
     */
    @Test
    void charitable_zeroSlots() {
        assertEquals(0, OmniblobQuickCraft.charitablePerSlot(5000, 0));
    }

    /**
     * Volume less than slot count: each slot gets zero.
     */
    @Test
    void charitable_volumeLessThanSlots() {
        assertEquals(0, OmniblobQuickCraft.charitablePerSlot(2, 5));
    }

    /**
     * Large volume: 100,000 mB across 4 slots = 25,000 each.
     */
    @Test
    void charitable_largeVolume() {
        assertEquals(25_000, OmniblobQuickCraft.charitablePerSlot(100_000, 4));
    }

    // -- greedyPerSlot (decision right-drag-over-one-blob-places-blobs) --

    /**
     * Holding more than one blob, a right-drag places one blob per slot:
     * 5,000 mB and 1,001 mB both answer 1,000 mB.
     *
     * @param carriedVolume the volume on the cursor
     */
    @ParameterizedTest
    @ValueSource(ints = {5_000, 1_001})
    void greedyPlacesOneBlobAboveOneBlob(int carriedVolume) {
        assertEquals(BlobStacks.MB_PER_BLOB, OmniblobQuickCraft.greedyPerSlot(carriedVolume));
    }

    /**
     * Holding one blob or less, a right-drag places one microblob per slot:
     * 1,000 mB, 500 mB and 1 mB answer 1 mB.
     *
     * @param carriedVolume the volume on the cursor
     */
    @ParameterizedTest
    @ValueSource(ints = {1_000, 500, 1})
    void greedyPlacesOneMicroblobAtOneBlobOrLess(int carriedVolume) {
        assertEquals(1, OmniblobQuickCraft.greedyPerSlot(carriedVolume));
    }

    /**
     * The boundary sits above one blob: 1,001 mB answers 1,000 mB, 1,000 mB answers 1 mB.
     */
    @Test
    void greedyBoundarySitsAboveOneBlob() {
        assertEquals(1_000, OmniblobQuickCraft.greedyPerSlot(1_001));
        assertEquals(1, OmniblobQuickCraft.greedyPerSlot(1_000));
    }
}
