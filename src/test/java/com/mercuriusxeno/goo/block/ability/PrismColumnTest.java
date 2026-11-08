package com.mercuriusxeno.goo.block.ability;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The prism's voxel shape is one cuboid around the column it draws: 8 pixels across
 * its corners, its flats narrower, 14 pixels out of the landing face
 * (decision prism-is-one-pointed-quartz-column; operator ruling: a cuboid).
 */
class PrismColumnTest {

    private static final double PIXEL = 1.0 / 16;
    private static final double EPSILON = 1e-5;
    private static final double ROUNDING = 1e5;
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

    /**
     * Each facing's shape at the bounds the prism answered before its base point and column moved
     * into PrismColumn and CrystalPrism, so the move changes no shape.
     */
    @ParameterizedTest
    @CsvSource({
        "DOWN,  0.25,    0.125,   0.28349, 0.75,    1.0,     0.71651",
        "UP,    0.25,    0.0,     0.28349, 0.75,    0.875,   0.71651",
        "NORTH, 0.25,    0.28349, 0.125,   0.75,    0.71651, 1.0",
        "SOUTH, 0.25,    0.28349, 0.0,     0.75,    0.71651, 0.875",
        "WEST,  0.125,   0.25,    0.28349, 1.0,     0.75,    0.71651",
        "EAST,  0.0,     0.25,    0.28349, 0.875,   0.75,    0.71651"})
    void eachFacingsShapeKeepsItsRecordedBounds(Direction facing, double minX, double minY, double minZ,
                                                 double maxX, double maxY, double maxZ) {
        AABB bounds = PrismColumn.shapeFor(facing).bounds();
        assertEquals(new AABB(minX, minY, minZ, maxX, maxY, maxZ).toString(),
                new AABB(round(bounds.minX), round(bounds.minY), round(bounds.minZ),
                        round(bounds.maxX), round(bounds.maxY), round(bounds.maxZ)).toString());
    }

    private static double round(double value) {
        return Math.round(value * ROUNDING) / ROUNDING;
    }

    @Test
    void theShapeIsOneCuboid() {
        for (Direction facing : Direction.values()) {
            VoxelShape shape = PrismColumn.shapeFor(facing);
            assertEquals(1, shape.toAabbs().size(), "the shape for " + facing + " is not one cuboid");
        }
    }
}
