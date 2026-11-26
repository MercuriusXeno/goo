package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.lunar.Lunar;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.Noise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for lunar's mountain ranges: across a stretch holding a range and a basin, the highest peak stands far
 * over the lowest basin floor, and the peak is anorthosite under its regolith.
 * decision lunar-surface-gains-giant-craters-mountains-rilles-and-boulder-fields
 */
@RegistersGameTests
public final class LunarMountainTests {
    private static final String LUNAR_RANGES_RISE_OVER_THE_BASINS = "lunar_ranges_rise_over_the_basins";

    private static final ResourceKey<NormalNoise> MARIA = ResourceKey.create(Registries.NOISE,
            Identifier.fromNamespaceAndPath(Goo.MODID, "lunar_maria"));
    /** The stretch sampled reaches this far from the origin each way, in blocks. */
    private static final int STRETCH_REACH = 1024;
    /** The sampled grid's spacing in blocks. */
    private static final int SAMPLE_STEP = 16;
    /** The least rise a range's peak makes over the lowest basin floor, in blocks. */
    private static final int LEAST_RISE = 40;
    /** How deep under a peak's surface its stone is read, past the regolith skin. */
    private static final int REGOLITH_SEARCH_DEPTH = 12;

    private static final String LUNAR_REQUIRED = "The server should hold goo:lunar";
    private static final String NO_BASIN = "The stretch within %d blocks of the origin should hold a basin";
    private static final String SHOULD_RISE = "The highest surface %s at y %d should stand %d blocks over the lowest "
            + "basin floor %s at y %d, stands %d";
    private static final String PEAK_IS_ANORTHOSITE = "The peak %s should read anorthosite under its regolith, reads %s";

    private LunarMountainTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, LUNAR_RANGES_RISE_OVER_THE_BASINS,
                LunarMountainTests::lunarRangesRiseOverTheBasins);
    }

    /**
     * Samples goo:lunar's generated surface height on a grid across a stretch around the origin through its chunk
     * generator; the highest surface stands at least 80 blocks over the lowest surface where the maria noise reads
     * basin, and the highest column reads anorthosite under its regolith.
     *
     * @param helper the gametest helper
     */
    public static void lunarRangesRiseOverTheBasins(GameTestHelper helper) {
        ServerLevel lunar = helper.getLevel().getServer().getLevel(Lunar.LEVEL);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
        ChunkGenerator generator = lunar.getChunkSource().getGenerator();
        RandomState randomState = lunar.getChunkSource().randomState();
        Noise maria = randomState.getOrCreateNoise(MARIA);
        BlockPos peak = null;
        BlockPos basinFloor = null;
        for (int x = -STRETCH_REACH; x <= STRETCH_REACH; x += SAMPLE_STEP) {
            for (int z = -STRETCH_REACH; z <= STRETCH_REACH; z += SAMPLE_STEP) {
                int surfaceY = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, lunar, randomState) - 1;
                BlockPos column = new BlockPos(x, surfaceY, z);
                if (peak == null || surfaceY > peak.getY()) {
                    peak = column;
                }
                boolean basin = maria.get(x, 0.0, z) > 0.0;
                if (basin && (basinFloor == null || surfaceY < basinFloor.getY())) {
                    basinFloor = column;
                }
            }
        }
        helper.assertTrue(basinFloor != null, String.format(NO_BASIN, STRETCH_REACH));
        int rise = peak.getY() - basinFloor.getY();
        helper.assertTrue(rise >= LEAST_RISE, String.format(SHOULD_RISE, peak, peak.getY(), LEAST_RISE, basinFloor,
                basinFloor.getY(), rise));
        BlockState stone = stoneUnderTheRegolith(lunar, peak);
        helper.assertTrue(stone.is(GooBlocks.ANORTHOSITE.get()), String.format(PEAK_IS_ANORTHOSITE, peak, stone));
        helper.succeed();
    }

    private static BlockState stoneUnderTheRegolith(ServerLevel lunar, BlockPos column) {
        lunar.getChunk(column.getX() >> 4, column.getZ() >> 4);
        int surfaceY = lunar.getHeight(Heightmap.Types.WORLD_SURFACE, column.getX(), column.getZ()) - 1;
        for (int y = surfaceY; y > surfaceY - REGOLITH_SEARCH_DEPTH; y--) {
            BlockState state = lunar.getBlockState(column.atY(y));
            if (!state.is(GooBlocks.REGOLITH.get()) && !state.is(GooBlocks.METEORIC_IRON.get())) {
                return state;
            }
        }
        return lunar.getBlockState(column.atY(surfaceY - REGOLITH_SEARCH_DEPTH));
    }
}
