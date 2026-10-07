package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.throwing.StreamCone;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The floors a spray reaches: the floors a stream's rays land on, or each
 * open cell a burst's sphere holds that stands on a sturdy floor, answered
 * as the floor block beneath it (decision mycosis-spore-stream-buds-and-poisons).
 */
public final class FloorReach {

    private FloorReach() {
    }

    /**
     * The floors a stream's spray lands on: rays leave the apex at random
     * headings inside the cone, each stopping at the first block it strikes
     * within the range, and a ray landing on a block's top face beneath an
     * open cell sprays that floor. Nothing behind a block is reached, and a
     * spray aimed at the ground at the thrower's feet lands there.
     *
     * @param level       the level
     * @param shooter     the spraying entity, which the rays pass through
     * @param apex        the cone's apex
     * @param axis        the cone's axis
     * @param range       the cone's reach in blocks
     * @param coneDegrees the cone's apex angle in degrees
     * @param rays        how many rays the spray casts
     * @param random      the random source turning each ray
     * @return the distinct floors struck
     */
    public static List<BlockPos> struckInCone(Level level, Entity shooter, Vec3 apex, Vec3 axis, double range,
                                              double coneDegrees, int rays, RandomSource random) {
        Set<BlockPos> floors = new LinkedHashSet<>();
        for (int i = 0; i < rays; i++) {
            Vec3 heading = StreamCone.launchDirection(axis, coneDegrees, Math.sqrt(random.nextDouble()),
                    random.nextDouble());
            BlockHitResult hit = level.clip(new ClipContext(apex, apex.add(heading.scale(range)),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
            if (hit.getType() == HitResult.Type.BLOCK && hit.getDirection() == Direction.UP
                    && isOpenFloor(level, hit.getBlockPos().above())) {
                floors.add(hit.getBlockPos());
            }
        }
        return new ArrayList<>(floors);
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
