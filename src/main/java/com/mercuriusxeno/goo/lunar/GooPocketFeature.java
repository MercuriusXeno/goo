package com.mercuriusxeno.goo.lunar;

import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.IntProviders;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * A pocket of world goo on lunar's ground: a few cells of one goo type's world goo, the type the biome's rule rolls,
 * laid on open ground tops around the spot, holding no more layers together than the pocket's volume, so a pocket is
 * worth gathering and never floods the ground around it. Its cells level out as any goo cell does, holding their
 * layers, and stand until something takes them; the pockets recur across the landscape as the feature's spacing
 * places them.
 * decision frost-astral-and-xeno-goo-spawn-periodically-on-lunar
 * decision crystal-and-metal-goo-spawn-in-the-highlands-and-maria
 *
 * @param cells  how many cells the pocket tries to lay
 * @param layers the layers each cell tries to hold
 * @param volume the most layers the pocket's cells hold together
 * @param reach  the farthest a cell lies from the pocket's middle, in blocks
 */
public record GooPocketFeature(IntProvider cells, IntProvider layers, int volume, int reach) implements Feature {

    /** The most cells a pocket names, each holding at most a whole block. */
    static final int MOST_CELLS = 9;

    /** The feature type's codec, naming the pocket's cells, their layers, its volume and its reach. */
    public static final MapCodec<GooPocketFeature> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            IntProviders.codec(1, MOST_CELLS).fieldOf("cells").forGetter(GooPocketFeature::cells),
            IntProviders.codec(1, VolumeCellBlock.MAX_LAYERS).fieldOf("layers").forGetter(GooPocketFeature::layers),
            Codec.intRange(1, MOST_CELLS * VolumeCellBlock.MAX_LAYERS).fieldOf("volume")
                    .forGetter(GooPocketFeature::volume),
            Codec.intRange(0, 3).fieldOf("reach").forGetter(GooPocketFeature::reach)
    ).apply(instance, GooPocketFeature::new));

    /** A cell's ground is sought this far over and under the pocket's height. */
    static final int GROUND_SEARCH = 2;
    private static final int PLACE_FLAGS = Block.UPDATE_CLIENTS;

    /** Each pocket goo type's world goo cell. */
    private static final Map<ResourceKey<GooTypeDefinition>, Supplier<? extends Block>> WORLD_GOO = Map.of(
            GooTypes.ASTRAL, GooBlocks.ASTRAL_DOWSING,
            GooTypes.FROST, GooBlocks.FROST_RIME,
            GooTypes.XENO, GooBlocks.XENO_OOZE,
            GooTypes.CRYSTAL, GooBlocks.CRYSTAL_SHATTER,
            GooTypes.METAL, GooBlocks.METAL_SPIKES);

    @Override
    public MapCodec<GooPocketFeature> codec() {
        return CODEC;
    }

    /**
     * The world goo cell a pocket of a goo type lays.
     *
     * @param gooType the pocket's goo type
     * @return its cell, empty for a goo type no pocket holds
     */
    public static Optional<Block> worldGooOf(ResourceKey<GooTypeDefinition> gooType) {
        return Optional.ofNullable(WORLD_GOO.get(gooType)).map(Supplier::get);
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        if (!opensOnGround(level, origin)) {
            return false;
        }
        Optional<Block> cell = level.getBiome(origin).unwrapKey()
                .flatMap(biome -> LunarGooPockets.gooTypeFor(biome, random))
                .flatMap(GooPocketFeature::worldGooOf);
        if (cell.isEmpty()) {
            return false;
        }
        layCells(level, random, origin, cell.get());
        return true;
    }

    /**
     * Lays the pocket's cells, the first at its middle and the rest on open ground around it, each holding its rolled
     * layers until the pocket's volume runs out.
     *
     * @param level  the level being generated
     * @param random the feature's random
     * @param origin the pocket's middle
     * @param cell   the world goo cell the pocket lays
     */
    private void layCells(WorldGenLevel level, RandomSource random, BlockPos origin, Block cell) {
        int left = volume;
        int count = cells.sample(random);
        for (int laid = 0; laid < count && left > 0; laid++) {
            Optional<BlockPos> spot = laid == 0 ? Optional.of(origin) : groundNear(level, random, origin);
            if (spot.isPresent()) {
                int held = Math.min(layers.sample(random), left);
                level.setBlock(spot.get(), cell.defaultBlockState().setValue(VolumeCellBlock.LAYERS, held), PLACE_FLAGS);
                left -= held;
            }
        }
    }

    /**
     * An open ground top at a random spot within the pocket's reach, the one nearest the pocket's height.
     *
     * @param level  the level being generated
     * @param random the feature's random
     * @param origin the pocket's middle
     * @return the spot over the ground's top, empty where the search finds none
     */
    private Optional<BlockPos> groundNear(WorldGenLevel level, RandomSource random, BlockPos origin) {
        BlockPos column = origin.offset(random.nextIntBetweenInclusive(-reach, reach), 0,
                random.nextIntBetweenInclusive(-reach, reach));
        for (int dy = GROUND_SEARCH; dy >= -GROUND_SEARCH; dy--) {
            BlockPos spot = column.above(dy);
            if (opensOnGround(level, spot)) {
                return Optional.of(spot);
            }
        }
        return Optional.empty();
    }

    private static boolean opensOnGround(WorldGenLevel level, BlockPos spot) {
        BlockPos under = spot.below();
        return level.getBlockState(spot).isAir() && level.getBlockState(under).isFaceSturdy(level, under, Direction.UP);
    }
}
