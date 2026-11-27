package com.mercuriusxeno.goo.lunar;

import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import java.util.ArrayList;
import java.util.List;

/**
 * The open spots over the warrens' tunnel floors that the warrens' features build on: a spot is open air with open
 * air over it, standing on a block whose top is sturdy, inside the warrens' band.
 * decision warrens-hold-xeno-rich-alien-blocks
 */
final class WarrenFloors {

    /** How many columns near the origin one search tries for a floor. */
    static final int COLUMN_TRIES = 24;
    /** How far from the origin a searched column lies, each way, so the search stays inside the decorated chunks. */
    static final int COLUMN_REACH = 7;
    /** How many columns across the square the search draws from, each way. */
    private static final int COLUMN_SPAN = 2 * COLUMN_REACH + 1;
    /** How far from a spot, each way, a monolith marker would touch it, diagonals included. */
    private static final int TOUCH_REACH = 1;

    private WarrenFloors() {
    }

    /**
     * Whether a height lies in the warrens' band, where a spot may open with the air over it inside the band too.
     *
     * @param y the spot's height
     * @return whether a spot may open there
     */
    static boolean opensInWarrens(int y) {
        return y > LunarDepths.SEA_ZONE_TOP_Y && y + 1 < LunarDepths.WARREN_ZONE_TOP_Y;
    }

    /**
     * One open floor spot from the columns near an origin, searched in random order.
     *
     * @param level  the level being generated
     * @param random the feature's random
     * @param origin the placed origin
     * @return an open spot over a floor with no monolith marker touching it, or null where no tried column holds one
     */
    static BlockPos findNear(WorldGenLevel level, RandomSource random, BlockPos origin) {
        for (int attempt = 0; attempt < COLUMN_TRIES; attempt++) {
            int x = origin.getX() + random.nextInt(COLUMN_SPAN) - COLUMN_REACH;
            int z = origin.getZ() + random.nextInt(COLUMN_SPAN) - COLUMN_REACH;
            List<BlockPos> floors = openSpotsIn(level, x, z);
            if (!floors.isEmpty()) {
                return floors.get(random.nextInt(floors.size()));
            }
        }
        return null;
    }

    /**
     * Every open spot in one column of the warrens' band, with no monolith marker touching it.
     *
     * @param level the level being generated
     * @param x     the column's x
     * @param z     the column's z
     * @return each open spot over a sturdy floor
     */
    private static List<BlockPos> openSpotsIn(WorldGenLevel level, int x, int z) {
        List<BlockPos> floors = new ArrayList<>();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int y = LunarDepths.SEA_ZONE_TOP_Y + 1; opensInWarrens(y); y++) {
            at.set(x, y, z);
            if (opensOverAFloor(level, at) && !touchesAMarker(level, at)) {
                floors.add(at.immutable());
            }
        }
        return floors;
    }

    /**
     * Whether a spot is open air with open air over it, on a block whose top is sturdy.
     *
     * @param level the level being generated
     * @param spot  the spot
     * @return whether the spot opens over a floor
     */
    static boolean opensOverAFloor(WorldGenLevel level, BlockPos spot) {
        BlockPos floor = spot.below();
        return level.getBlockState(spot).isAir() && level.getBlockState(spot.above()).isAir()
                && level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP);
    }

    /**
     * Whether a monolith marker stands in any block touching a spot, diagonals included.
     *
     * @param level the level being generated
     * @param spot  the spot
     * @return whether a marker touches it
     */
    static boolean touchesAMarker(WorldGenLevel level, BlockPos spot) {
        for (BlockPos near : BlockPos.betweenClosed(spot.offset(-TOUCH_REACH, -TOUCH_REACH, -TOUCH_REACH),
                spot.offset(TOUCH_REACH, TOUCH_REACH, TOUCH_REACH))) {
            if (level.getBlockState(near).is(GooBlocks.MONOLITH_MARKER.get())) {
                return true;
            }
        }
        return false;
    }
}
