package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where Glitter's front meets a block face: the arc's points lie both on
 * the sphere and on the face, a sphere missing the face draws nothing, and
 * a face the front has passed lies inside its farthest reach
 * (decision glitter-sphere-icons-gem-ore-groups).
 */
class SurfaceArcsTest {

    private static final double TOLERANCE = 1e-6;
    private static final Vec3 CENTER = new Vec3(0.5, 1.9, 0.5);

    @Test
    void anArcOnTheFloorLiesOnTheSphereAndOnTheFace() {
        BlockPos floor = new BlockPos(4, 0, 0);
        double radius = 4.2;

        List<List<Vec3>> arcs = SurfaceArcs.arcs(CENTER, radius, floor, Direction.UP);

        assertEquals(1, arcs.size());
        assertTrue(arcs.getFirst().size() >= 2);
        for (Vec3 point : arcs.getFirst()) {
            assertEquals(radius, point.distanceTo(CENTER), TOLERANCE);
            assertEquals(1, point.y, TOLERANCE);
            assertTrue(point.x >= 4 - TOLERANCE && point.x <= 5 + TOLERANCE);
            assertTrue(point.z >= -TOLERANCE && point.z <= 1 + TOLERANCE);
        }
    }

    @Test
    void anArcOnAWallLiesOnTheSphereAndOnTheWall() {
        BlockPos wall = new BlockPos(3, 2, 0);

        for (List<Vec3> arc : SurfaceArcs.arcs(CENTER, 2.7, wall, Direction.WEST)) {
            for (Vec3 point : arc) {
                assertEquals(2.7, point.distanceTo(CENTER), TOLERANCE);
                assertEquals(3, point.x, TOLERANCE);
            }
        }
        assertTrue(!SurfaceArcs.arcs(CENTER, 2.7, wall, Direction.WEST).isEmpty());
    }

    @Test
    void aSphereMissingTheFaceDrawsNothingAndAPassedFaceLiesWithinItsFarthest() {
        BlockPos floor = new BlockPos(10, 0, 0);

        assertTrue(SurfaceArcs.arcs(CENTER, 3, floor, Direction.UP).isEmpty());
        double farthest = SurfaceArcs.farthest(CENTER, floor, Direction.UP);
        assertTrue(SurfaceArcs.arcs(CENTER, farthest + 0.01, floor, Direction.UP).isEmpty());
    }
}
