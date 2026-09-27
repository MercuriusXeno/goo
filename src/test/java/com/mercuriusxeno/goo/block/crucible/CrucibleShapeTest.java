package com.mercuriusxeno.goo.block.crucible;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that the crucible's collision shape is the goocible model with the
 * drawn cavity cut out, its bounds read from {@link CrucibleBasin}.
 */
class CrucibleShapeTest {

    private static final double EPSILON = 1e-9;
    private static final double BASIN_CENTER = 0.5;
    /** A probe's distance off a face, well inside the solid or the air beside it. */
    private static final double NUDGE = 1.0 / 64.0;
    private static final VoxelShape SHAPE = CrucibleShape.SHAPE;

    @Test
    void basinCenterColumnTopsOutAtTheDrawnFloor() {
        assertEquals(CrucibleBasin.FLOOR_Y, SHAPE.max(Direction.Axis.Y, BASIN_CENTER, BASIN_CENTER), EPSILON);
    }

    @Test
    void cavitySpansTheFootprintFromFloorToRim() {
        assertEquals(CrucibleBasin.FOOTPRINT_MIN, CrucibleShape.CAVITY.min(Direction.Axis.X), EPSILON);
        assertEquals(CrucibleBasin.FOOTPRINT_MAX, CrucibleShape.CAVITY.max(Direction.Axis.Z), EPSILON);
        assertEquals(CrucibleBasin.FLOOR_Y, CrucibleShape.CAVITY.min(Direction.Axis.Y), EPSILON);
        assertEquals(CrucibleBasin.RIM_Y, CrucibleShape.CAVITY.max(Direction.Axis.Y), EPSILON);
    }

    @Test
    void airFillsTheCavityUpToTheRim() {
        assertFalse(solidAt(BASIN_CENTER, CrucibleBasin.FLOOR_Y + NUDGE, BASIN_CENTER));
        assertFalse(solidAt(CrucibleBasin.FOOTPRINT_MIN + NUDGE, CrucibleBasin.RIM_Y - NUDGE,
            CrucibleBasin.FOOTPRINT_MAX - NUDGE));
    }

    @Test
    void floorAndWallsAreSolid() {
        assertTrue(solidAt(BASIN_CENTER, CrucibleBasin.FLOOR_Y - NUDGE, BASIN_CENTER));
        assertTrue(solidAt(CrucibleBasin.FOOTPRINT_MIN - NUDGE, CrucibleBasin.RIM_Y - NUDGE, BASIN_CENTER));
        assertTrue(solidAt(BASIN_CENTER, CrucibleBasin.RIM_Y - NUDGE, CrucibleBasin.FOOTPRINT_MAX + NUDGE));
    }

    @Test
    void outerLedgeStopsAtTheBodyTop() {
        assertTrue(solidAt(NUDGE, CrucibleShape.LEDGE_Y - NUDGE, BASIN_CENTER));
        assertFalse(solidAt(NUDGE, CrucibleShape.LEDGE_Y + NUDGE, BASIN_CENTER));
        assertEquals(CrucibleShape.COLLAR_MIN, SHAPE.min(Direction.Axis.X, CrucibleBasin.RIM_Y - NUDGE, BASIN_CENTER),
            EPSILON);
    }

    /**
     * @return true when a box of the shape holds the point
     */
    private static boolean solidAt(double x, double y, double z) {
        for (AABB box : SHAPE.toAabbs()) {
            if (box.contains(x, y, z)) { return true; }
        }
        return false;
    }
}
