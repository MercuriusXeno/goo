package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import java.util.List;

/**
 * Lunar's four surface biomes, and where a test finds ground deep inside each: the biome source answers a column's
 * surface biome from the noise alone, so no chunk generates while a test searches.
 * decision lunar-surface-has-maria-highlands-shadowed-basins-and-eldritch-ground
 */
final class LunarSurfaceBiomes {

    static final ResourceKey<Biome> MARIA = key("lunar_maria");
    static final ResourceKey<Biome> HIGHLANDS = key("lunar_highlands");
    static final ResourceKey<Biome> SHADOWED_BASINS = key("lunar_shadowed_basins");
    static final ResourceKey<Biome> ELDRITCH_GROUND = key("lunar_eldritch_ground");
    static final List<ResourceKey<Biome>> ALL = List.of(MARIA, HIGHLANDS, SHADOWED_BASINS, ELDRITCH_GROUND);

    /** A surface biome is read at this height, over the warrens' band, where the depth reads none. */
    private static final int SURFACE_PROBE_Y = 64;
    /** A spot stands deep in its biome when the biome holds this far out from it every way. */
    private static final int DEEP_REACH = 64;
    /** A search for a spot walks out from the origin in steps this wide, to this far each way. */
    private static final int SEARCH_STEP = 64;
    private static final int SEARCH_REACH = 8192;

    private LunarSurfaceBiomes() {
    }

    private static ResourceKey<Biome> key(String path) {
        return ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(Goo.MODID, path));
    }

    /**
     * The surface biome over a column, as the biome source places it.
     *
     * @param lunar the level
     * @param x     the column's x
     * @param z     the column's z
     * @return the biome
     */
    static Holder<Biome> surfaceBiomeAt(ServerLevel lunar, int x, int z) {
        return lunar.getChunkSource().getGenerator().getBiomeSource().createResolver(lunar.getChunkSource()
                .randomState().createClimateSampler(SamplerContext.EMPTY_UNCACHED)).getNoiseBiome(QuartPos.fromBlock(x),
                QuartPos.fromBlock(SURFACE_PROBE_Y), QuartPos.fromBlock(z));
    }

    /**
     * Whether a block is the ground a surface biome lays at its top: basalt in maria, regolith in the highlands,
     * breccia or impact glass in the shadowed basins, and a sculk family block on eldritch ground.
     * decision lunar-biomes-add-block-types-to-the-ground
     *
     * @param biome the biome
     * @param state the block
     * @return true where the block is that biome's ground
     */
    static boolean isGroundOf(Holder<Biome> biome, BlockState state) {
        if (biome.is(MARIA)) {
            return state.is(Blocks.BASALT);
        }
        if (biome.is(SHADOWED_BASINS)) {
            return state.is(GooBlocks.BRECCIA.get()) || state.is(GooBlocks.IMPACT_GLASS.get());
        }
        if (biome.is(ELDRITCH_GROUND)) {
            return isSculkFamily(state);
        }
        return state.is(GooBlocks.REGOLITH.get());
    }

    /**
     * Whether a block is one a surface biome lays over the stone: regolith, breccia, impact glass or a sculk family
     * block.
     *
     * @param state the block
     * @return true where the block is laid ground
     */
    static boolean isLaidGround(BlockState state) {
        return state.is(GooBlocks.REGOLITH.get()) || state.is(GooBlocks.BRECCIA.get())
                || state.is(GooBlocks.IMPACT_GLASS.get()) || isSculkFamily(state);
    }

    /**
     * Whether a block is of the sculk family.
     *
     * @param state the block
     * @return true for sculk, its vein, catalyst, sensor and shrieker
     */
    static boolean isSculkFamily(BlockState state) {
        return state.is(Blocks.SCULK) || state.is(Blocks.SCULK_VEIN) || state.is(Blocks.SCULK_CATALYST)
                || state.is(Blocks.SCULK_SENSOR) || state.is(Blocks.SCULK_SHRIEKER);
    }

    /**
     * The column nearest the origin, on a square walk outward, standing deep inside a surface biome.
     *
     * @param lunar the level
     * @param biome the biome
     * @return the column at y 0, or null where the search finds none
     */
    static BlockPos spotIn(ServerLevel lunar, ResourceKey<Biome> biome) {
        for (int ring = 0; ring <= SEARCH_REACH; ring += SEARCH_STEP) {
            for (int x = -ring; x <= ring; x += SEARCH_STEP) {
                for (int z = -ring; z <= ring; z += SEARCH_STEP) {
                    boolean onRing = Math.abs(x) == ring || Math.abs(z) == ring;
                    if (onRing && deepIn(lunar, biome, x, z)) {
                        return new BlockPos(x, 0, z);
                    }
                }
            }
        }
        return null;
    }

    private static boolean deepIn(ServerLevel lunar, ResourceKey<Biome> biome, int x, int z) {
        return surfaceBiomeAt(lunar, x, z).is(biome) && surfaceBiomeAt(lunar, x + DEEP_REACH, z).is(biome)
                && surfaceBiomeAt(lunar, x - DEEP_REACH, z).is(biome) && surfaceBiomeAt(lunar, x, z + DEEP_REACH).is(biome)
                && surfaceBiomeAt(lunar, x, z - DEEP_REACH).is(biome);
    }
}
