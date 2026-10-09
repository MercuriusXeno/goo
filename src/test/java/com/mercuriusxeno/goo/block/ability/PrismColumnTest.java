package com.mercuriusxeno.goo.block.ability;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The prism's voxel shape is one cuboid around the column it draws: 8 pixels across
 * its corners, its flats narrower, 14 pixels out of the landing face
 * (decision prism-is-one-pointed-quartz-column; operator ruling: a cuboid).
 */
class PrismColumnTest {

    private static final double PIXEL = 1.0 / 16;
    private static final double EPSILON = 1e-5;
    private static final double HALF_FLATS = 4 * Math.cos(Math.PI / 6) * PIXEL;

    @Test
    void anUprightPrismsShapeBoundsTheColumnOnTheFloor() {
        AABB bounds = PrismColumn.shapeFor(Direction.UP).bounds();
        assertEquals(0, bounds.minY, EPSILON);
        assertEquals(14 * PIXEL, bounds.maxY, EPSILON);
        assertEquals(8 * PIXEL, bounds.getXsize(), EPSILON);
        assertEquals(2 * HALF_FLATS, bounds.getZsize(), EPSILON);
        assertEquals(0.5, bounds.getCenter().x, EPSILON);
        assertEquals(0.5, bounds.getCenter().z, EPSILON);
    }

    @Test
    void aWallPrismsShapeStandsOutOfTheFaceItLandedOn() {
        AABB bounds = PrismColumn.shapeFor(Direction.NORTH).bounds();
        assertEquals(1, bounds.maxZ, EPSILON);
        assertEquals(1 - 14 * PIXEL, bounds.minZ, EPSILON);
        assertEquals(0.5, bounds.getCenter().x, EPSILON);
        assertEquals(0.5, bounds.getCenter().y, EPSILON);
    }

    @Test
    void theShapeIsOneCuboid() {
        for (Direction facing : Direction.values()) {
            VoxelShape shape = PrismColumn.shapeFor(facing);
            assertEquals(1, shape.toAabbs().size(), "the shape for " + facing + " is not one cuboid");
        }
    }
}
