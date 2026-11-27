package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.throwing.ConeShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The floors a spray reaches: each open cell inside a burst's sphere, answered
 * as the floor block below it, and nothing past the sphere; and the floors a
 * stream's rays land on, each ray inside the cone's shape.
 */
class FloorReachTest {

    private static final int FLOOR_Y = 0;
    private static final Vec3 APEX = new Vec3(0.5, 1.5, 0.5);
    private static final Vec3 EAST = new Vec3(1, 0, 0);
    private static final double RANGE = 8;
    private static final int RAYS = 2000;
    private static final long SEED = 4723;
    /** The band of headings, in degrees off the axis, a floor stands across: inside 30 either side, past 4 up. */
    private static final double FLOOR_NEAR = 15;
    private static final double FLOOR_FAR = 28;
    /** How far a heading may lean the other way and still meet a floor. */
    private static final double FLOOR_THICKNESS = 3;
    private static final BlockPos SIDE_FLOOR = new BlockPos(5, FLOOR_Y, 2);
    private static final BlockPos HIGH_FLOOR = new BlockPos(5, 3, 0);

    /** Every cell one above the floor layer is open over a floor. */
    private static boolean overTheFloor(BlockPos cell) {
        return cell.getY() == FLOOR_Y + 1;
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

    /**
     * cone-style-abilities-are-reviewed-for-consistency: a cone 60 degrees across and 8 up and down sprays the
     * floor standing 15 to 28 degrees off to the side of its axis and never the floor standing as far above it.
     */
    @Test
    void aWideThinConeSpraysAFloorOffToTheSideAndNoneAsFarAbove() {
        List<BlockPos> floors = FloorReach.struckInCone(levelWithTwoFloors(), mock(Entity.class), APEX, EAST, RANGE,
                new ConeShape(60, 8, false), RAYS, RandomSource.create(SEED));

        assertTrue(floors.contains(SIDE_FLOOR), "the floor off to the side was never sprayed: " + floors);
        assertFalse(floors.contains(HIGH_FLOOR), "the floor as far above the axis was sprayed: " + floors);
    }

    /**
     * A level whose clip lands a ray heading 15 to 28 degrees across the axis on the side floor's top face, and
     * one heading as far up on the high floor's, every other ray flying free; each floor is sturdy under open air.
     */
    private static Level levelWithTwoFloors() {
        Level level = mock(Level.class);
        when(level.clip(any(ClipContext.class))).thenAnswer(inv -> landingOf(inv.getArgument(0)));
        BlockState floor = mock(BlockState.class);
        when(floor.isFaceSturdy(any(), any(), eq(Direction.UP))).thenReturn(true);
        BlockState air = mock(BlockState.class);
        when(air.isAir()).thenReturn(true);
        when(level.getBlockState(any())).thenAnswer(inv -> {
            BlockPos pos = inv.getArgument(0);
            return pos.equals(SIDE_FLOOR) || pos.equals(HIGH_FLOOR) ? floor : air;
        });
        return level;
    }

    private static BlockHitResult landingOf(ClipContext ray) {
        Vec3 heading = ray.getTo().subtract(ray.getFrom()).normalize();
        double across = Math.toDegrees(Math.atan2(heading.z, heading.x));
        double up = Math.toDegrees(Math.atan2(heading.y, heading.x));
        if (across >= FLOOR_NEAR && across <= FLOOR_FAR && Math.abs(up) <= FLOOR_THICKNESS) {
            return new BlockHitResult(Vec3.atCenterOf(SIDE_FLOOR), Direction.UP, SIDE_FLOOR, false);
        }
        if (up >= FLOOR_NEAR && up <= FLOOR_FAR && Math.abs(across) <= FLOOR_THICKNESS) {
            return new BlockHitResult(Vec3.atCenterOf(HIGH_FLOOR), Direction.UP, HIGH_FLOOR, false);
        }
        return BlockHitResult.miss(ray.getTo(), Direction.UP, BlockPos.containing(ray.getTo()));
    }
}
