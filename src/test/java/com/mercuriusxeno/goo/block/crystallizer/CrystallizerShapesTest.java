package com.mercuriusxeno.goo.block.crystallizer;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Covers the crystallizer's hit shape: the growing crystal joins it from its first mB, below the dial's tier. */
class CrystallizerShapesTest {

    private static final double PIXELS_PER_BLOCK = 16.0;
    private static final double EPSILON = 1e-9;
    /** Far below a chrysm's volume, so the crystal is growing under a dial at chrysm. */
    private static final long GROWING = 1000;

    @Test
    void growingCrystalReachesAboveTheBlockTopByItsHeight() {
        AABB bounds = CrystallizerShapes.hitShape(Direction.NORTH, GROWING).bounds();
        double height = CrystalCluster.reach(GROWING)[1] / PIXELS_PER_BLOCK;
        assertEquals(1.0 + height, bounds.maxY, EPSILON);
    }

    @Test
    void nothingCrystallizedStopsAtTheBlockTop() {
        AABB bounds = CrystallizerShapes.hitShape(Direction.NORTH, 0).bounds();
        assertEquals(1.0, bounds.maxY, EPSILON);
    }
}
