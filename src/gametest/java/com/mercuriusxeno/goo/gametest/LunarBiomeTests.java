package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.lunar.CraterField;
import com.mercuriusxeno.goo.lunar.GiantCraterField;
import com.mercuriusxeno.goo.lunar.LavaTubes;
import com.mercuriusxeno.goo.lunar.Lunar;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.RandomState;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Gametests for lunar's four surface biomes: a wide grid of surface samples finds maria, highlands, shadowed basins and
 * eldritch ground, and each lays its own ground, maria basalt, the highlands regolith over anorthosite and eldritch
 * ground the sculk family; and the highlands stand over the maria, whose ground varies less.
 * decision lunar-surface-has-maria-highlands-shadowed-basins-and-eldritch-ground
 * decision lunar-biomes-add-block-types-to-the-ground
 */
@RegistersGameTests
public final class LunarBiomeTests {
    private static final String LUNAR_SURFACE_HOLDS_FOUR_BIOMES = "lunar_surface_holds_four_biomes";
    private static final String LUNAR_BIOMES_LAY_THEIR_OWN_GROUND = "lunar_biomes_lay_their_own_ground";
    private static final String LUNAR_HIGHLANDS_STAND_OVER_THE_MARIA = "lunar_highlands_stand_over_the_maria";

    /** The grid reaches this far each way from the origin, a sample every step. */
    private static final int GRID_REACH = 4096;
    private static final int GRID_STEP = 256;
    /** A column for a ground read is sought this far each way from a spot deep in its biome. */
    private static final int COLUMN_REACH = 256;
    private static final int COLUMN_STEP = 8;
    /** A ground read's column stands this far inside its biome every way. */
    private static final int BIOME_MARGIN = 8;

    /** Heights are sampled this far each way from a spot deep in a biome, a sample every step. */
    private static final int HEIGHT_REACH = 768;
    private static final int HEIGHT_STEP = 16;
    /** A biome's height reads from at least this many samples standing in it. */
    private static final int LEAST_SAMPLES = 400;

    private static final String LUNAR_REQUIRED = "The server should hold goo:lunar";
    private static final String HOLDS_ALL_FOUR = "A grid of surface samples within %d blocks of the origin should find "
            + "%s, finds %s";
    private static final String NO_SPOT = "No spot within the search lies deep in %s";
    private static final String NO_COLUMN = "No column near %s in %s stands clear of every crater, ray, boulder and "
            + "tube";
    private static final String TOO_FEW_SAMPLES = "Only %d height samples near %s stand in %s, fewer than %d";
    private static final String HIGHLANDS_STAND_HIGHER = "The highlands' mean surface height %.1f should stand over "
            + "the maria's %.1f";
    private static final String MARIA_VARY_LESS = "The maria's surface height should vary less than the highlands', "
            + "deviates %.1f against %.1f";
    private static final String MARIA_READS_BASALT = "The maria column %s should read basalt at its surface y %d, "
            + "reads %s";
    private static final String HIGHLANDS_READ_REGOLITH = "The highlands column %s should read regolith at its "
            + "surface y %d, reads %s";
    private static final String UNDER_THE_REGOLITH = "The highlands column %s should read anorthosite under its "
            + "regolith at y %d, reads %s";
    private static final String ELDRITCH_READS_SCULK = "The eldritch ground column %s should read a sculk family "
            + "block at its surface y %d, reads %s";

    private LunarBiomeTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, LUNAR_SURFACE_HOLDS_FOUR_BIOMES, LunarBiomeTests::lunarSurfaceHoldsFourBiomes);
        GooTestFunctions.registerTest(registrar, LUNAR_BIOMES_LAY_THEIR_OWN_GROUND,
                LunarBiomeTests::lunarBiomesLayTheirOwnGround);
        GooTestFunctions.registerTest(registrar, LUNAR_HIGHLANDS_STAND_OVER_THE_MARIA,
                LunarBiomeTests::lunarHighlandsStandOverTheMaria);
    }

    /**
     * Samples goo:lunar's biome at each surface point of a wide grid, at the height the generator gives the column's
     * surface; every one of the four surface biomes appears.
     *
     * @param helper the gametest helper
     */
    public static void lunarSurfaceHoldsFourBiomes(GameTestHelper helper) {
        ServerLevel lunar = helper.getLevel().getServer().getLevel(Lunar.LEVEL);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
        RandomState randomState = lunar.getChunkSource().randomState();
        Set<ResourceKey<Biome>> found = new HashSet<>();
        for (int x = -GRID_REACH; x <= GRID_REACH; x += GRID_STEP) {
            for (int z = -GRID_REACH; z <= GRID_REACH; z += GRID_STEP) {
                int surfaceY = lunar.getChunkSource().getGenerator().getBaseHeight(x, z,
                        Heightmap.Types.WORLD_SURFACE_WG, lunar, randomState);
                lunar.getChunkSource().getGenerator().getBiomeSource().createResolver(randomState
                        .createClimateSampler(SamplerContext.EMPTY_UNCACHED)).getNoiseBiome(x >> 2, surfaceY >> 2, z >> 2)
                        .unwrapKey().ifPresent(found::add);
            }
        }
        helper.assertTrue(found.containsAll(LunarSurfaceBiomes.ALL),
                String.format(HOLDS_ALL_FOUR, GRID_REACH, LunarSurfaceBiomes.ALL, found));
        helper.succeed();
    }

    /**
     * Finds a column deep in maria, the highlands and eldritch ground, each clear of every crater, ray, thrown boulder
     * and tube; maria's surface reads basalt, the highlands' regolith with anorthosite under it, and eldritch ground's
     * a sculk family block.
     *
     * @param helper the gametest helper
     */
    public static void lunarBiomesLayTheirOwnGround(GameTestHelper helper) {
        ServerLevel lunar = helper.getLevel().getServer().getLevel(Lunar.LEVEL);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
        Predicate<BlockPos> clear = clearGround(lunar);
        BlockPos maria = columnIn(helper, lunar, LunarSurfaceBiomes.MARIA, clear);
        BlockState mariaTop = lunar.getBlockState(surface(lunar, maria));
        helper.assertTrue(mariaTop.is(Blocks.BASALT),
                String.format(MARIA_READS_BASALT, maria, surface(lunar, maria).getY(), mariaTop));
        BlockPos highlands = columnIn(helper, lunar, LunarSurfaceBiomes.HIGHLANDS, clear);
        assertRegolithOverAnorthosite(helper, lunar, highlands);
        BlockPos eldritch = columnIn(helper, lunar, LunarSurfaceBiomes.ELDRITCH_GROUND, clear);
        BlockState eldritchTop = lunar.getBlockState(surface(lunar, eldritch));
        helper.assertTrue(LunarSurfaceBiomes.isSculkFamily(eldritchTop),
                String.format(ELDRITCH_READS_SCULK, eldritch, surface(lunar, eldritch).getY(), eldritchTop));
        helper.succeed();
    }

    /**
     * Samples goo:lunar's generated surface height on a grid around a spot deep in the highlands and one deep in
     * maria, keeping the samples standing in each biome; the highlands' mean stands over the maria's, and the maria's
     * heights deviate less from their mean than the highlands' do.
     *
     * @param helper the gametest helper
     */
    public static void lunarHighlandsStandOverTheMaria(GameTestHelper helper) {
        ServerLevel lunar = helper.getLevel().getServer().getLevel(Lunar.LEVEL);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
        double[] highlands = heightsIn(helper, lunar, LunarSurfaceBiomes.HIGHLANDS);
        double[] maria = heightsIn(helper, lunar, LunarSurfaceBiomes.MARIA);
        helper.assertTrue(highlands[0] > maria[0], String.format(HIGHLANDS_STAND_HIGHER, highlands[0], maria[0]));
        helper.assertTrue(maria[1] < highlands[1], String.format(MARIA_VARY_LESS, maria[1], highlands[1]));
        helper.succeed();
    }

    /** The mean and the standard deviation of the surface heights sampled in a biome around a spot deep in it. */
    private static double[] heightsIn(GameTestHelper helper, ServerLevel lunar, ResourceKey<Biome> biome) {
        BlockPos spot = LunarSurfaceBiomes.spotIn(lunar, biome);
        helper.assertTrue(spot != null, String.format(NO_SPOT, biome.identifier()));
        RandomState randomState = lunar.getChunkSource().randomState();
        double sum = 0.0;
        double squares = 0.0;
        int count = 0;
        for (int x = spot.getX() - HEIGHT_REACH; x <= spot.getX() + HEIGHT_REACH; x += HEIGHT_STEP) {
            for (int z = spot.getZ() - HEIGHT_REACH; z <= spot.getZ() + HEIGHT_REACH; z += HEIGHT_STEP) {
                if (!LunarSurfaceBiomes.surfaceBiomeAt(lunar, x, z).is(biome)) {
                    continue;
                }
                double height = lunar.getChunkSource().getGenerator().getBaseHeight(x, z,
                        Heightmap.Types.WORLD_SURFACE_WG, lunar, randomState);
                sum += height;
                squares += height * height;
                count++;
            }
        }
        helper.assertTrue(count >= LEAST_SAMPLES, String.format(TOO_FEW_SAMPLES, count, spot, biome.identifier(),
                LEAST_SAMPLES));
        double mean = sum / count;
        return new double[] {mean, Math.sqrt(Math.max(0.0, squares / count - mean * mean))};
    }

    private static void assertRegolithOverAnorthosite(GameTestHelper helper, ServerLevel lunar, BlockPos column) {
        BlockPos top = surface(lunar, column);
        BlockState topState = lunar.getBlockState(top);
        helper.assertTrue(topState.is(GooBlocks.REGOLITH.get()),
                String.format(HIGHLANDS_READ_REGOLITH, column, top.getY(), topState));
        BlockPos under = top.below();
        while (lunar.getBlockState(under).is(GooBlocks.REGOLITH.get())) {
            under = under.below();
        }
        BlockState underState = lunar.getBlockState(under);
        helper.assertTrue(underState.is(GooBlocks.ANORTHOSITE.get()),
                String.format(UNDER_THE_REGOLITH, column, under.getY(), underState));
    }

    /** A column near a spot deep in a biome, in the biome and clear of every feature that lays its own ground. */
    private static BlockPos columnIn(GameTestHelper helper, ServerLevel lunar, ResourceKey<Biome> biome,
            Predicate<BlockPos> clear) {
        BlockPos spot = LunarSurfaceBiomes.spotIn(lunar, biome);
        helper.assertTrue(spot != null, String.format(NO_SPOT, biome.identifier()));
        for (int dx = -COLUMN_REACH; dx <= COLUMN_REACH; dx += COLUMN_STEP) {
            for (int dz = -COLUMN_REACH; dz <= COLUMN_REACH; dz += COLUMN_STEP) {
                BlockPos column = spot.offset(dx, 0, dz);
                if (inBiomeWithMargin(lunar, column, biome) && clear.test(column)) {
                    return column;
                }
            }
        }
        helper.fail(String.format(NO_COLUMN, spot, biome.identifier()));
        return spot;
    }

    /**
     * Whether a column and its neighbours a margin out every way stand in a biome, so the surface rule's biome
     * lookup, which vanilla's biome zoom jitters by a few blocks, reads the same biome at the column.
     */
    private static boolean inBiomeWithMargin(ServerLevel lunar, BlockPos column, ResourceKey<Biome> biome) {
        for (int dx = -BIOME_MARGIN; dx <= BIOME_MARGIN; dx += BIOME_MARGIN) {
            for (int dz = -BIOME_MARGIN; dz <= BIOME_MARGIN; dz += BIOME_MARGIN) {
                if (!LunarSurfaceBiomes.surfaceBiomeAt(lunar, column.getX() + dx, column.getZ() + dz).is(biome)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Passes a column no crater, crater rim, ray, thrown boulder or lava tube reaches. */
    private static Predicate<BlockPos> clearGround(ServerLevel lunar) {
        RandomState randomState = lunar.getChunkSource().randomState();
        CraterField craters = CraterField.seeded(randomState.getOrCreateNoise(CraterField.NOISE), randomState);
        GiantCraterField giants = GiantCraterField.seeded(randomState.getOrCreateNoise(CraterField.NOISE), randomState);
        LavaTubes tubes = LavaTubes.seeded(randomState.getOrCreateNoise(LavaTubes.NOISE));
        ClearOfBoulders clearOfBoulders = ClearOfBoulders.in(lunar);
        return column -> craters.isOpenGroundAt(column.getX(), column.getZ())
                && !craters.isOnRayAt(column.getX(), column.getZ()) && !giants.isOnRayAt(column.getX(), column.getZ())
                && !giants.reachesBandAt(column.getX(), column.getZ(), 0.0, 1.0 + GiantCraterField.RIM_REACH, 0.0)
                && !tubes.crossesColumnAt(column.getX(), column.getZ()) && clearOfBoulders.test(column);
    }

    private static BlockPos surface(ServerLevel lunar, BlockPos column) {
        lunar.getChunk(column.getX() >> 4, column.getZ() >> 4);
        return column.atY(lunar.getHeight(Heightmap.Types.WORLD_SURFACE, column.getX(), column.getZ()) - 1);
    }
}
