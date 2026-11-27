package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.lunar.Lunar;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.Noise;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for lunar's seam on the live server: the terrain lunar generates, its biomes and its surface rule's noise
 * read the same just past +7,500,000 as just past -7,500,000 on x and z, through the random state the server seeds
 * lunar with, while the overworld's noise repeats nowhere.
 * decision moon-wraps-at-its-border-like-the-zone
 */
@RegistersGameTests
public final class LunarSeamTests {

    private static final String LUNAR_TERRAIN_REPEATS_ACROSS_THE_SEAM = "lunar_terrain_repeats_across_the_seam";
    private static final int SEAM = 7_500_000;
    private static final int[] PAST_THE_SEAM = {0, 1, 7, 40, 333, 641, 1_919};
    private static final int[] ACROSS = {0, 2_345, -SEAM + 3};
    private static final int[] HEIGHTS = {-110, -40, 0, 30, 60, 120};

    private static final String LUNAR_REQUIRED = "The server should hold goo:lunar";
    private static final String NOISE_GENERATOR = "goo:lunar should generate from noise settings, generates from %s";
    private static final String TERRAIN = "Lunar's terrain should read %s at %s, %s, %s as at %s, %s, %s, reads %s";
    private static final String BIOME = "Lunar's biome should read %s at quart %s, %s as at quart %s, %s, reads %s";
    private static final String SURFACE_NOISE = "Lunar's surface noise should read %s at %s, %s as at %s, %s, reads %s";
    private static final String OVERWORLD_REPEATS = "The overworld's surface noise should read apart just past either"
            + " lunar edge, reads the same at every step past them";

    private LunarSeamTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, LUNAR_TERRAIN_REPEATS_ACROSS_THE_SEAM,
                LunarSeamTests::lunarTerrainRepeatsAcrossTheSeam);
    }

    /**
     * Lunar's final density, its biome and its surface noise read the same at each step past the east edge as at
     * that step past the west edge, and likewise past the south edge and the north; the overworld's surface noise
     * reads apart there.
     *
     * @param helper the gametest helper
     */
    public static void lunarTerrainRepeatsAcrossTheSeam(GameTestHelper helper) {
        ServerLevel lunar = helper.getLevel().getServer().getLevel(Lunar.LEVEL);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
        ChunkGenerator generator = lunar.getChunkSource().getGenerator();
        helper.assertTrue(generator instanceof NoiseBasedChunkGenerator,
                String.format(NOISE_GENERATOR, generator.getClass().getSimpleName()));
        NoiseRouter router = ((NoiseBasedChunkGenerator) generator).generatorSettings().value().noiseRouter();
        RandomState randomState = lunar.getChunkSource().randomState();
        BiomeResolver biomes = generator.getBiomeSource().createUncachedResolver(randomState);
        Noise surface = randomState.getOrCreateNoise(Noises.SURFACE);
        for (int past : PAST_THE_SEAM) {
            for (int across : ACROSS) {
                for (int y : HEIGHTS) {
                    assertTerrainRepeats(helper, randomState, router, -SEAM + past, y, across, SEAM + past, y, across);
                    assertTerrainRepeats(helper, randomState, router, across, y, -SEAM + past, across, y, SEAM + past);
                }
                assertBiomeRepeats(helper, biomes, QuartPos.fromBlock(-SEAM + past),
                        QuartPos.fromBlock(across), QuartPos.fromBlock(SEAM + past), QuartPos.fromBlock(across));
                assertSurfaceNoiseRepeats(helper, surface, -SEAM + past, across, SEAM + past, across);
                assertSurfaceNoiseRepeats(helper, surface, across, -SEAM + past, across, SEAM + past);
            }
        }
        helper.assertTrue(overworldSurfaceReadsApartPastTheSeam(helper.getLevel().getServer().getLevel(Level.OVERWORLD)),
                OVERWORLD_REPEATS);
        helper.succeed();
    }

    private static void assertTerrainRepeats(GameTestHelper helper, RandomState randomState, NoiseRouter router,
                                             int westX, int westY, int westZ, int eastX, int eastY, int eastZ) {
        float west = randomState.sampleBlockValueUncached(router.finalDensity(), westX, westY, westZ);
        float east = randomState.sampleBlockValueUncached(router.finalDensity(), eastX, eastY, eastZ);
        helper.assertTrue(west == east,
                String.format(TERRAIN, west, eastX, eastY, eastZ, westX, westY, westZ, east));
    }

    private static void assertBiomeRepeats(GameTestHelper helper, BiomeResolver biomes, int westQuartX, int quartZ,
                                           int eastQuartX, int eastQuartZ) {
        int quartY = QuartPos.fromBlock(0);
        Holder<Biome> west = biomes.getNoiseBiome(westQuartX, quartY, quartZ);
        Holder<Biome> east = biomes.getNoiseBiome(eastQuartX, quartY, eastQuartZ);
        helper.assertTrue(west.equals(east), String.format(BIOME, west.getRegisteredName(), eastQuartX, eastQuartZ,
                westQuartX, quartZ, east.getRegisteredName()));
    }

    private static void assertSurfaceNoiseRepeats(GameTestHelper helper, Noise surface, int westX, int westZ,
                                                  int eastX, int eastZ) {
        float west = surface.get(westX, 0.0, westZ);
        float east = surface.get(eastX, 0.0, eastZ);
        helper.assertTrue(west == east, String.format(SURFACE_NOISE, west, eastX, eastZ, westX, westZ, east));
    }

    private static boolean overworldSurfaceReadsApartPastTheSeam(ServerLevel overworld) {
        Noise surface = overworld.getChunkSource().randomState().getOrCreateNoise(Noises.SURFACE);
        for (int past : PAST_THE_SEAM) {
            if (surface.get(-SEAM + past, 0.0, 0.0) != surface.get(SEAM + past, 0.0, 0.0)) {
                return true;
            }
        }
        return false;
    }
}
