package com.mercuriusxeno.goo.lunar;

import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import java.util.ArrayList;
import java.util.List;

/**
 * One monolith marker standing alone on a warren tunnel's floor: from the placed origin, a handful of columns close by
 * are searched down the warrens' band for a floor, a sturdy block under two blocks of air with no marker touching the
 * spot, and the marker stands on one of the floors found, its width turned along a random axis. Nothing stands outside
 * the warrens' band.
 * decision warrens-hold-xeno-rich-alien-blocks
 * decision each-lunar-zone-holds-its-own-deposit-richer-by-depth
 */
public final class MonolithMarkerFeature implements Feature {

    /** The feature type's codec, which reads no settings. */
    public static final MapCodec<MonolithMarkerFeature> CODEC = MapCodec.unit(MonolithMarkerFeature::new);

    /** How many columns near the origin one placement searches for a floor. */
    static final int COLUMN_TRIES = 24;
    /** How far from the origin a searched column lies, each way, so the search stays inside the decorated chunks. */
    static final int COLUMN_REACH = 7;
    /** How many columns across the square the search draws from, each way. */
    private static final int COLUMN_SPAN = 2 * COLUMN_REACH + 1;
    /** How far from a spot, each way, another marker would touch it, diagonals included. */
    private static final int TOUCH_REACH = 1;
    private static final int PLACE_FLAGS = Block.UPDATE_CLIENTS;

    /** Creates the feature, which takes no configuration. */
    public MonolithMarkerFeature() {
    }

    @Override
    public MapCodec<MonolithMarkerFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        for (int attempt = 0; attempt < COLUMN_TRIES; attempt++) {
            int x = origin.getX() + random.nextInt(COLUMN_SPAN) - COLUMN_REACH;
            int z = origin.getZ() + random.nextInt(COLUMN_SPAN) - COLUMN_REACH;
            List<BlockPos> floors = floorsIn(level, x, z);
            if (!floors.isEmpty()) {
                Direction.Axis axis = random.nextBoolean() ? Direction.Axis.X : Direction.Axis.Z;
                level.setBlock(floors.get(random.nextInt(floors.size())), GooBlocks.MONOLITH_MARKER.get()
                        .defaultBlockState().setValue(MonolithMarkerBlock.AXIS, axis), PLACE_FLAGS);
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a height lies in the warrens' band, where a marker may stand with the air over it inside the band too.
     *
     * @param y the marker's height
     * @return whether a marker may stand there
     */
    static boolean standsInWarrens(int y) {
        return y > LunarDepths.SEA_ZONE_TOP_Y && y + 1 < LunarDepths.WARREN_ZONE_TOP_Y;
    }

    /**
     * Every spot in one column of the warrens' band a marker may stand on.
     *
     * @param level the level being generated
     * @param x the column's x
     * @param z the column's z
     * @return each open spot over a sturdy floor, under air, with no marker touching it
     */
    private static List<BlockPos> floorsIn(WorldGenLevel level, int x, int z) {
        List<BlockPos> floors = new ArrayList<>();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int y = LunarDepths.SEA_ZONE_TOP_Y + 1; standsInWarrens(y); y++) {
            at.set(x, y, z);
            if (opensOverAFloor(level, at) && !touchesAMarker(level, at)) {
                floors.add(at.immutable());
            }
        }
        return floors;
    }

    private static boolean opensOverAFloor(WorldGenLevel level, BlockPos spot) {
        BlockPos floor = spot.below();
        return level.getBlockState(spot).isAir() && level.getBlockState(spot.above()).isAir()
                && level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP);
    }

    private static boolean touchesAMarker(WorldGenLevel level, BlockPos spot) {
        for (BlockPos near : BlockPos.betweenClosed(spot.offset(-TOUCH_REACH, -TOUCH_REACH, -TOUCH_REACH),
                spot.offset(TOUCH_REACH, TOUCH_REACH, TOUCH_REACH))) {
            if (level.getBlockState(near).is(GooBlocks.MONOLITH_MARKER.get())) {
                return true;
            }
        }
        return false;
    }
}
