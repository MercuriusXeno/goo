package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The floors a spray reaches: each open cell inside the cone or sphere,
 * answered as the floor block below it, and nothing behind the cone or past
 * the sphere.
 */
class FloorReachTest {

    private static final int FLOOR_Y = 0;
    private static final Vec3 EAST = new Vec3(1, 0, 0);
    private static final Vec3 APEX = new Vec3(0.5, 1.5, 0.5);
    private static final double RANGE = 5;
    private static final double CONE_DEGREES = 30;

    /** Every cell one above the floor layer is open over a floor. */
    private static boolean overTheFloor(BlockPos cell) {
        return cell.getY() == FLOOR_Y + 1;
    }

    @Test
    void coneAnswersTheFloorBelowEachOpenCellAheadOfIt() {
        List<BlockPos> floors = FloorReach.inCone(APEX, EAST, RANGE, CONE_DEGREES, FloorReachTest::overTheFloor);

        assertTrue(floors.contains(new BlockPos(3, FLOOR_Y, 0)), floors.toString());
        assertTrue(floors.stream().allMatch(floor -> floor.getY() == FLOOR_Y), floors.toString());
    }

    @Test
    void coneReachesNoFloorBehindOrBesideIt() {
        List<BlockPos> floors = FloorReach.inCone(APEX, EAST, RANGE, CONE_DEGREES, FloorReachTest::overTheFloor);

        assertFalse(floors.contains(new BlockPos(-2, FLOOR_Y, 0)), floors.toString());
        assertFalse(floors.contains(new BlockPos(1, FLOOR_Y, 3)), floors.toString());
        assertTrue(floors.stream().allMatch(floor -> floor.getX() > 0), floors.toString());
    }

    @Test
    void sphereAnswersEveryFloorWithinItsRadiusAndNoneBeyond() {
        Vec3 center = new Vec3(0.5, 1.5, 0.5);
        List<BlockPos> floors = FloorReach.inSphere(center, 2, FloorReachTest::overTheFloor);

        assertTrue(floors.contains(new BlockPos(0, FLOOR_Y, 0)), floors.toString());
        assertTrue(floors.contains(new BlockPos(-2, FLOOR_Y, 0)), floors.toString());
        assertFalse(floors.contains(new BlockPos(2, FLOOR_Y, 2)), floors.toString());
        assertEquals(13, floors.size(), floors.toString());
    }

    @Test
    void aCellTheTestRefusesIsNoFloor() {
        assertTrue(FloorReach.inSphere(new Vec3(0.5, 1.5, 0.5), 2, cell -> false).isEmpty());
    }
}
