package com.mercuriusxeno.goo.ability;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void theDragSetsHowFarTheLookSwingsOffThePinAtAnyRange() {
        Vec3 eye = Vec3.ZERO;
        Vec3 pin = new Vec3(0, 0, 40);
        Vec3 swungAway = new Vec3(5, 0, 40).normalize();
        double expected = pin.distanceTo(swungAway.scale(pin.dot(swungAway)));
        assertEquals(expected, DragSize.dragged(pin, eye, swungAway), DELTA);
        assertEquals(DragSize.MIN_RADIUS, DragSize.dragged(pin, eye, new Vec3(0, 0, 1)), DELTA);
    }

    @Test
    void aLookTurnedAwayFromThePinMeasuresFromTheEye() {
        Vec3 pin = new Vec3(0, 0, 10);
        assertEquals(10, DragSize.dragged(pin, Vec3.ZERO, new Vec3(0, 0, -1)), DELTA);
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
    void anAffordableRadiusIsTheLargestWholeOneTheHoldingsPayFor() {
        int holdings = 2_345;
        double radius = DragSize.affordable(50, REFERENCE_COST, holdings);
        assertEquals(Math.floor(radius), radius, DELTA);
        assertTrue(DragSize.costAt(REFERENCE_COST, radius) <= holdings);
        assertTrue(DragSize.costAt(REFERENCE_COST, radius + 1) > holdings);
    }

    // black-hole-leaves-a-compression-sphere: the hole takes whole blocks, so it is charged for a whole radius
    @Test
    void aDragBetweenWholeRadiiOpensAtTheWholeRadiusUnderIt() {
        assertEquals(3, DragSize.affordable(3.9, REFERENCE_COST, 100 * REFERENCE_COST), DELTA);
    }
}
