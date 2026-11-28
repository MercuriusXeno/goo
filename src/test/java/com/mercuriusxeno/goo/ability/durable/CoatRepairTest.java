package com.mercuriusxeno.goo.ability.durable;

import com.mercuriusxeno.goo.data.GooFraction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A layer of Coat restores the share of an item's full durability that the layer's worth is of the item's whole
 * worth, rounded down, at least one point, never past full, and nothing to an item with nothing to restore.
 * items-dropped-in-durable-goo-are-repaired-by-their-worth
 */
class CoatRepairTest {

    private static final GooFraction LAYER = GooFraction.of(100);
    private static final GooFraction ITEM = GooFraction.of(1000);
    private static final int FULL = 500;

    @Test
    void aLayerRestoresItsShareOfTheItemsWorth() {
        assertEquals(50, CoatRepair.pointsRestored(LAYER, ITEM, FULL, FULL));
    }

    @Test
    void aTinyShareRestoresOnePoint() {
        assertEquals(1, CoatRepair.pointsRestored(GooFraction.of(1), GooFraction.of(1_000_000), FULL, FULL));
    }

    @Test
    void anItemShortOfFullRegainsNoMoreThanItLacks() {
        assertEquals(10, CoatRepair.pointsRestored(LAYER, ITEM, FULL, 10));
    }

    @Test
    void anUndamagedItemIsRestoredNothing() {
        assertEquals(0, CoatRepair.pointsRestored(LAYER, ITEM, FULL, 0));
    }

    @Test
    void anItemWithNoWorthIsRestoredNothing() {
        assertEquals(0, CoatRepair.pointsRestored(LAYER, GooFraction.of(0), FULL, FULL));
    }
}
