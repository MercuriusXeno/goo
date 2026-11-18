package com.mercuriusxeno.goo.ability;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A Build's footprint stays on its corner's level within the span and rise caps the server checks (decision spire-rips-walls-and-platforms). */
class BuildFootprintTest {

    private static final BlockPos CORNER = new BlockPos(0, 64, 0);

    @Test
    void aDragPastTheSpanIsPulledBackOntoTheCornersLevel() {
        BuildFootprint footprint = BuildFootprint.capped(CORNER, new BlockPos(20, 70, -20), 3);

        assertEquals(new BlockPos(BuildFootprint.MAX_SPAN - 1, 64, -(BuildFootprint.MAX_SPAN - 1)),
                footprint.opposite());
        assertTrue(footprint.withinCaps());
    }

    @Test
    void aRiseAskedPastTheCapsIsHeldWithinThem() {
        assertEquals(BuildFootprint.MAX_RISE, BuildFootprint.capped(CORNER, CORNER, 99).rise());
        assertEquals(BuildFootprint.MIN_RISE, BuildFootprint.capped(CORNER, CORNER, 0).rise());
    }

    @Test
    void aSubmitPastAnyCapIsRefused() {
        assertFalse(new BuildFootprint(CORNER, new BlockPos(BuildFootprint.MAX_SPAN, 64, 0), 1).withinCaps());
        assertFalse(new BuildFootprint(CORNER, new BlockPos(0, 64, BuildFootprint.MAX_SPAN), 1).withinCaps());
        assertFalse(new BuildFootprint(CORNER, new BlockPos(1, 65, 1), 1).withinCaps());
        assertFalse(new BuildFootprint(CORNER, CORNER, BuildFootprint.MAX_RISE + 1).withinCaps());
        assertFalse(new BuildFootprint(CORNER, CORNER, 0).withinCaps());
    }

    @Test
    void theGroundCellsCoverTheRectangleBetweenTheCorners() {
        BuildFootprint footprint = new BuildFootprint(new BlockPos(2, 64, 5), new BlockPos(0, 64, 4), 1);

        assertEquals(6, footprint.groundCells().size());
        assertEquals(new BlockPos(0, 64, 4), footprint.min());
        assertEquals(new BlockPos(2, 64, 5), footprint.max());
    }
}
