package com.mercuriusxeno.goo.lunar;

import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A pocket of astral world goo beside one of lunar's cave deposits: from a spot in the cave bands, it seeks a deposit
 * facing a cave close by and lays a Dowsing pocket on the cave's floor beside it; a spot with no deposit near lays
 * none, so astral goo coincides with the valuables sporadically rather than everywhere, and the cave bands try their
 * pockets more densely than the surface does.
 * decision frost-astral-and-xeno-goo-spawn-periodically-on-lunar
 *
 * @param deposits the blocks a pocket sits beside
 * @param seek     how far from the spot a deposit is sought, each way
 * @param shape    the pocket's cells, their layers, its volume and its reach
 */
public record DepositGooPocketFeature(TagKey<Block> deposits, int seek, GooPocketShape shape) implements Feature {

    /** The farthest a deposit is sought from the spot. */
    static final int MOST_SEEK = 8;
    /** The cave floor a pocket lies on is sought this far from its deposit, each way. */
    static final int FLOOR_REACH = 2;

    /** The feature type's codec, naming the deposits, how far they are sought and the pocket's shape. */
    public static final MapCodec<DepositGooPocketFeature> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            TagKey.codec(Registries.BLOCK).fieldOf("deposits").forGetter(DepositGooPocketFeature::deposits),
            Codec.intRange(1, MOST_SEEK).fieldOf("seek").forGetter(DepositGooPocketFeature::seek),
            GooPocketShape.MAP_CODEC.forGetter(DepositGooPocketFeature::shape)
    ).apply(instance, DepositGooPocketFeature::new));

    @Override
    public MapCodec<DepositGooPocketFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        List<BlockPos> near = depositsFacingCave(level, origin);
        if (near.isEmpty()) {
            return false;
        }
        Optional<BlockPos> floor = floorBeside(level, random, near.get(random.nextInt(near.size())));
        floor.ifPresent(spot -> shape.lay(level, random, spot, GooBlocks.ASTRAL_DOWSING.get()));
        return floor.isPresent();
    }

    /**
     * The deposit blocks within the seek of a spot that face a cave's air.
     *
     * @param level  the level being generated
     * @param origin the spot
     * @return the deposits near it
     */
    private List<BlockPos> depositsFacingCave(WorldGenLevel level, BlockPos origin) {
        List<BlockPos> near = new ArrayList<>();
        for (BlockPos spot : BlockPos.betweenClosed(origin.offset(-seek, -seek, -seek), origin.offset(seek, seek, seek))) {
            if (level.getBlockState(spot).is(deposits) && touchesAir(level, spot)) {
                near.add(spot.immutable());
            }
        }
        return near;
    }

    /**
     * An open spot over a ground top beside a deposit, where its pocket's first cell lies.
     *
     * @param level   the level being generated
     * @param random  the feature's random
     * @param deposit the deposit
     * @return the cave floor beside it, empty where none lies near
     */
    private static Optional<BlockPos> floorBeside(WorldGenLevel level, RandomSource random, BlockPos deposit) {
        List<BlockPos> floor = new ArrayList<>();
        for (BlockPos spot : BlockPos.betweenClosed(deposit.offset(-FLOOR_REACH, -FLOOR_REACH, -FLOOR_REACH),
                deposit.offset(FLOOR_REACH, FLOOR_REACH, FLOOR_REACH))) {
            if (GooPocketShape.opensOnGround(level, spot)) {
                floor.add(spot.immutable());
            }
        }
        return floor.isEmpty() ? Optional.empty() : Optional.of(floor.get(random.nextInt(floor.size())));
    }

    private static boolean touchesAir(WorldGenLevel level, BlockPos spot) {
        for (Direction side : Direction.values()) {
            if (level.getBlockState(spot.relative(side)).isAir()) {
                return true;
            }
        }
        return false;
    }
}
