package com.mercuriusxeno.goo.block.canister;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The slot centers a canister block reads from the machine below it: the fixed grid
 * unless an attachable overrides it, and the nearest-slot helpers over either.
 */
class CanisterSlotCentersTest {

    /** The grid with slot 0 moved to (4, 4) and slot 2 to (12, 4), as a crystallizer facing south lays it out. */
    private static final float[][] MOVED = {
        {4, 4}, {8, 3}, {12, 4}, {3, 8}, {8, 8}, {13, 8}, {3, 13}, {8, 13}, {13, 13}};

    private static final ICanisterAttachable OVERRIDING = new ICanisterAttachable() {
        @Override
        public int currentTopAttachments() {
            return 0;
        }

        @Override
        public float[][] slotCenters() {
            return MOVED;
        }
    };

    private static final ICanisterAttachable PLAIN = () -> 0;

    @Test
    void nothingBelowReadsTheGrid() {
        assertSame(CanisterSlotLayout.SLOT_CENTERS, CanisterSlotLayout.centersOf(null));
    }

    @Test
    void aPlainAttachableReadsTheGrid() {
        assertSame(CanisterSlotLayout.SLOT_CENTERS, CanisterSlotLayout.centersOf(PLAIN));
    }

    @Test
    void anOverridingAttachableReadsItsCenters() {
        assertSame(MOVED, CanisterSlotLayout.centersOf(OVERRIDING));
    }

    @Test
    void theGridsBlockCentersArePrecomputed() {
        assertSame(CanisterSlotLayout.SLOT_CENTERS_BLOCK, CanisterSlotLayout.blockCenters(CanisterSlotLayout.SLOT_CENTERS));
    }

    @Test
    void anOverridesBlockCentersAreKeptAndScaled() {
        float[][] block = CanisterSlotLayout.blockCenters(MOVED);
        assertSame(block, CanisterSlotLayout.blockCenters(MOVED));
        assertEquals(0.25f, block[0][0]);
        assertEquals(0.75f, block[2][0]);
    }

    @Test
    void aHitAtAMovedCenterAddressesItsSlot() {
        assertEquals(0, CanisterSlotLayout.nearestSlot(MOVED, 4, 4));
        assertEquals(2, CanisterSlotLayout.nearestSlot(MOVED, 12, 4));
    }

    @Test
    void aHitAtTheOldCenterStillReachesTheMovedSlot() {
        assertEquals(0, CanisterSlotLayout.nearestSlot(MOVED, 3, 3));
    }

    @Test
    void theNearestAllowedSlotTakesTheLowestIndexOnATie() {
        assertEquals(0, CanisterSlotLayout.nearestAllowed(MOVED, Set.of(0, 2), 8, 3));
        assertEquals(2, CanisterSlotLayout.nearestAllowed(MOVED, Set.of(0, 2), 11, 3));
    }

    @Test
    void leaningFromAMovedCenterPicksTheNeighbourItLeansTo() {
        assertEquals(1, CanisterSlotLayout.adjacentByCursorLean(MOVED, 0, 6, 4));
        assertEquals(3, CanisterSlotLayout.adjacentByCursorLean(MOVED, 0, 4, 6));
    }
}
