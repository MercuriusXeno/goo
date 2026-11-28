package com.mercuriusxeno.goo.lunar;

import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * A pocket of world goo on lunar's surface: a few cells of one goo type's world goo, the type the biome's rule rolls,
 * laid in the pocket's shape on open ground around the spot. The pockets recur across the landscape as the feature's
 * spacing places them.
 * decision frost-astral-and-xeno-goo-spawn-periodically-on-lunar
 * decision crystal-and-metal-goo-spawn-in-the-highlands-and-maria
 *
 * @param shape the pocket's cells, their layers, its volume and its reach
 */
public record GooPocketFeature(GooPocketShape shape) implements Feature {

    /** The feature type's codec, naming the pocket's shape. */
    public static final MapCodec<GooPocketFeature> CODEC = GooPocketShape.MAP_CODEC.xmap(GooPocketFeature::new,
            GooPocketFeature::shape);

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
        if (!GooPocketShape.opensOnGround(level, origin)) {
            return false;
        }
        Optional<Block> cell = level.getBiome(origin).unwrapKey()
                .flatMap(biome -> LunarGooPockets.gooTypeFor(biome, random))
                .flatMap(GooPocketFeature::worldGooOf);
        cell.ifPresent(goo -> shape.lay(level, random, origin, goo));
        return cell.isPresent();
    }
}
