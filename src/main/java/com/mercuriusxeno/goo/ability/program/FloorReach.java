package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.throwing.StreamCone;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The floors a spray reaches: each open cell whose center a stream's cone or
 * a burst's sphere holds and which stands on a sturdy floor, answered as the
 * floor block beneath it (decision mycosis-spore-stream-buds-and-poisons).
 */
public final class FloorReach {

    private FloorReach() {
    }

    /**
     * The floors under the open cells a stream's cone holds.
     *
     * @param apex        the cone's apex
     * @param axis        the cone's axis
     * @param range       the cone's reach in blocks
     * @param coneDegrees the cone's apex angle in degrees
     * @param openFloor   whether a cell is open and stands on a floor
     * @return the floor blocks, one below each such cell
     */
    public static List<BlockPos> inCone(Vec3 apex, Vec3 axis, double range, double coneDegrees,
                                        Predicate<BlockPos> openFloor) {
        return within(apex, range, cell -> StreamCone.contains(apex, axis, range, coneDegrees,
                Vec3.atCenterOf(cell)) && openFloor.test(cell));
    }

    /**
     * The floors under the open cells a sphere holds.
     *
     * @param center    the sphere's center
     * @param radius    the sphere's radius in blocks
     * @param openFloor whether a cell is open and stands on a floor
     * @return the floor blocks, one below each such cell
     */
    public static List<BlockPos> inSphere(Vec3 center, double radius, Predicate<BlockPos> openFloor) {
        return within(center, radius, cell -> Vec3.atCenterOf(cell).distanceTo(center) <= radius
                && openFloor.test(cell));
    }

    /**
     * Whether a cell is empty and stands on a block whose top face is sturdy.
     *
     * @param level the level
     * @param cell  the cell
     * @return true for an open cell over a floor
     */
    public static boolean isOpenFloor(LevelReader level, BlockPos cell) {
        BlockPos floor = cell.below();
        return level.getBlockState(cell).isAir() && level.getBlockState(floor).isFaceSturdy(level, floor,
                Direction.UP);
    }

    private static List<BlockPos> within(Vec3 center, double reach, Predicate<BlockPos> reached) {
        List<BlockPos> floors = new ArrayList<>();
        BlockPos low = BlockPos.containing(center.subtract(reach, reach, reach));
        BlockPos high = BlockPos.containing(center.add(reach, reach, reach));
        for (BlockPos cell : BlockPos.betweenClosed(low, high)) {
            if (reached.test(cell)) {
                floors.add(cell.below());
            }
        }
        return floors;
    }
}
