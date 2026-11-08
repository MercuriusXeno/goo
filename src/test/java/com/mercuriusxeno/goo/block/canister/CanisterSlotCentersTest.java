package com.mercuriusxeno.goo.block.canister;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The nearest-slot helpers over any slot centers they are handed, here a grid with two
 * slots moved off the fixed layout.
 */
class CanisterSlotCentersTest {

    /** The grid with slot 0 moved to (4, 4) and slot 2 to (12, 4). */
    private static final float[][] MOVED = {
        {4, 4}, {8, 3}, {12, 4}, {3, 8}, {8, 8}, {13, 8}, {3, 13}, {8, 13}, {13, 13}};

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

    @Test
    void aHitResolvesOnlyAmongSlotsInPlay() {
        float[][] centers = new float[CanisterSlotLayout.SLOT_COUNT][];
        for (int i = 0; i < centers.length; i++) {
            centers[i] = CanisterSlotLayout.SLOT_CENTERS[i].clone();
        }
        centers[0] = new float[] {4, 4};
        assertEquals(3, CanisterSlotLayout.nearestSlot(centers, 2, 6), "every slot in play: the grid's west slot is nearer");
        assertEquals(0, CanisterSlotLayout.nearestSlotInPlay(centers, Set.of(0, 2), 2, 6),
                "only slots in play: the moved slot answers");
        assertEquals(com.mercuriusxeno.goo.GooConstants.NO_SLOT, CanisterSlotLayout.nearestSlotInPlay(centers, Set.of(2), 2, 6),
                "a slot in play out of reach answers none");
    }
}
