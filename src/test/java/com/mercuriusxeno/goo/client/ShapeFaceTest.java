package com.mercuriusxeno.goo.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A face marker sits on the struck face of a block's own outline shape and
 * scales by the square root of that face's longer side: a full cube marks
 * its whole face at full size, a slab its half-height top, and a bud its
 * small top a little larger than the bud.
 */
class ShapeFaceTest {

    private static final double EPSILON = 1e-9;
    private static final BlockPos POS = new BlockPos(10, 64, -3);
    private static final AABB FULL = new AABB(0, 0, 0, 1, 1, 1);
    private static final AABB SLAB = new AABB(0, 0, 0, 1, 0.5, 1);
    /** A fungal bud's outline: five to eleven pixels across, four tall. */
    private static final AABB BUD = new AABB(5 / 16.0, 0, 5 / 16.0, 11 / 16.0, 4 / 16.0, 11 / 16.0);

    private static void assertPoint(Vec3 expected, Vec3 actual) {
        assertEquals(0, expected.distanceTo(actual), EPSILON, expected + " vs " + actual);
    }

    @Test
    void aFullCubeMarksItsWholeFaceAtFullSize() {
        ShapeFace top = ShapeFace.of(POS, Direction.UP, FULL);
        assertPoint(new Vec3(10.5, 65, -2.5), top.center());
        assertEquals(1, top.scale(), EPSILON);
        assertPoint(new Vec3(10, 64.5, -2.5), ShapeFace.of(POS, Direction.WEST, FULL).center());
    }

    @Test
    void aSlabMarksItsTopHalfwayUpAndItsSideAtFullWidth() {
        assertPoint(new Vec3(10.5, 64.5, -2.5), ShapeFace.of(POS, Direction.UP, SLAB).center());
        ShapeFace side = ShapeFace.of(POS, Direction.NORTH, SLAB);
        assertPoint(new Vec3(10.5, 64.25, -3), side.center());
        assertEquals(1, side.scale(), EPSILON);
    }

    @Test
    void aBudMarksItsOwnTopScaledByTheRootOfItsWidth() {
        ShapeFace top = ShapeFace.of(POS, Direction.UP, BUD);
        assertPoint(new Vec3(10.5, 64.25, -2.5), top.center());
        assertEquals(Math.sqrt(6 / 16.0), top.scale(), EPSILON);
        assertEquals(Math.sqrt(6 / 16.0), ShapeFace.of(POS, Direction.EAST, BUD).scale(), EPSILON);
    }

    @Test
    void noLevelMarksTheCubeFace() {
        assertPoint(new Vec3(10.5, 64.5, -2), ShapeFace.at(null, POS, Direction.SOUTH).center());
    }
}
