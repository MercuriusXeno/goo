package com.mercuriusxeno.goo.ability;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A world ability sized at will takes the radius its drag sets, never under
 * the smallest, costs by the volume of that radius against the reference
 * radius its JSON cost buys, and is cut back to the radius the holdings pay
 * for (decision black-hole-leaves-a-compression-sphere).
 */
class DragSizeTest {

    private static final double DELTA = 1e-9;
    private static final int REFERENCE_COST = 1000;

    @Test
    void theDragSetsTheDistanceFromThePinNeverUnderTheSmallest() {
        assertEquals(5, DragSize.dragged(Vec3.ZERO, new Vec3(3, 4, 0)), DELTA);
        assertEquals(DragSize.MIN_RADIUS, DragSize.dragged(Vec3.ZERO, new Vec3(0.2, 0, 0)), DELTA);
    }

    @Test
    void theCostGrowsWithTheVolume() {
        assertEquals(REFERENCE_COST, DragSize.costAt(REFERENCE_COST, DragSize.REFERENCE_RADIUS));
        assertEquals(8 * REFERENCE_COST, DragSize.costAt(REFERENCE_COST, 2 * DragSize.REFERENCE_RADIUS));
        assertEquals(REFERENCE_COST / 8, DragSize.costAt(REFERENCE_COST, DragSize.REFERENCE_RADIUS / 2));
    }

    @Test
    void theRadiusIsCutBackToWhatTheHoldingsPayFor() {
        assertEquals(DragSize.REFERENCE_RADIUS, DragSize.affordable(10, REFERENCE_COST, REFERENCE_COST), DELTA);
        assertEquals(4, DragSize.affordable(4, REFERENCE_COST, 100 * REFERENCE_COST), DELTA);
        assertEquals(DragSize.MIN_RADIUS, DragSize.affordable(10, REFERENCE_COST, 0), DELTA);
    }

    @Test
    void anAffordableRadiusCostsNoMoreThanTheHoldings() {
        int holdings = 2_345;
        double radius = DragSize.affordable(50, REFERENCE_COST, holdings);
        assertEquals(holdings, DragSize.costAt(REFERENCE_COST, radius), 1);
    }
}
