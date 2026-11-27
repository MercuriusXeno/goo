package com.mercuriusxeno.goo.lunar;

import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * One monolith marker standing alone on a warren tunnel's floor: from the placed origin, a handful of columns close by
 * are searched down the warrens' band for an open floor spot with no marker touching it, and the marker stands on
 * one, its width turned along a random axis. Nothing stands outside the warrens' band.
 * decision warrens-hold-xeno-rich-alien-blocks
 * decision each-lunar-zone-holds-its-own-deposit-richer-by-depth
 */
public final class MonolithMarkerFeature implements Feature {

    /** The feature type's codec, which reads no settings. */
    public static final MapCodec<MonolithMarkerFeature> CODEC = MapCodec.unit(MonolithMarkerFeature::new);

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
        BlockPos spot = WarrenFloors.findNear(level, random, origin);
        if (spot == null) {
            return false;
        }
        level.setBlock(spot, markerTurned(random), PLACE_FLAGS);
        return true;
    }

    /**
     * A monolith marker turned along a random horizontal axis.
     *
     * @param random the feature's random
     * @return the marker's state
     */
    static BlockState markerTurned(RandomSource random) {
        Direction.Axis axis = random.nextBoolean() ? Direction.Axis.X : Direction.Axis.Z;
        return GooBlocks.MONOLITH_MARKER.get().defaultBlockState().setValue(MonolithMarkerBlock.AXIS, axis);
    }
}
