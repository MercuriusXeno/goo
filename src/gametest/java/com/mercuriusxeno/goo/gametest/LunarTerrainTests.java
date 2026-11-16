package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.List;
import java.util.function.Consumer;

/**
 * Gametests for goo:lunar's terrain: it generates from its own noise settings, and a column reads regolith at the
 * surface over anorthosite or basalt, molten iron over the iron core at depth, and bedrock at the floor.
 * decision lunar-generates-from-its-own-noise-and-surface-rules
 * decision lunar-matter-is-anorthosite-basalt-regolith-and-an-iron-core
 */
@RegistersGameTests
public final class LunarTerrainTests {
    private static final String LUNAR_COLUMN_READS_ITS_STRATA = "lunar_column_reads_its_strata";

    private static final ResourceKey<Level> LUNAR = ResourceKey.create(Registries.DIMENSION,
            Identifier.fromNamespaceAndPath(Goo.MODID, "lunar"));
    private static final Identifier LUNAR_NOISE = Identifier.fromNamespaceAndPath(Goo.MODID, "lunar");
    /** Columns spread over a few chunks, so one crater or basin reads no different from open ground. */
    private static final List<BlockPos> TEST_SPOTS = List.of(
            new BlockPos(8, 0, 8), new BlockPos(-40, 0, 72), new BlockPos(120, 0, -24));
    /** The strata's named depths: bedrock at the floor, iron core to y 23, molten iron from y 24 to y 39. */
    private static final int FLOOR_Y = 0;
    private static final int IRON_CORE_TOP_Y = 23;
    private static final int MOLTEN_IRON_TOP_Y = 39;

    private static final String LUNAR_REQUIRED = "The server should hold goo:lunar";
    private static final String OWN_NOISE = "goo:lunar should generate from goo:lunar's noise settings, generates from %s";
    private static final String SURFACE = "%s should read regolith at the surface y %d, reads %s";
    private static final String BELOW_REGOLITH = "%s should read anorthosite or basalt under its regolith at y %d, reads %s";
    private static final String STRATUM = "%s should read %s at y %d, reads %s";

    private LunarTerrainTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, LUNAR_COLUMN_READS_ITS_STRATA, LunarTerrainTests::lunarColumnReadsItsStrata);
    }

    /**
     * Each test spot's column in goo:lunar reads regolith at its surface, anorthosite or basalt beneath, molten iron
     * from y 24 to y 39, the iron core from y 1 to y 23 and bedrock at y 0.
     *
     * @param helper the gametest helper
     */
    public static void lunarColumnReadsItsStrata(GameTestHelper helper) {
        ServerLevel lunar = helper.getLevel().getServer().getLevel(LUNAR);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
        ChunkGenerator generator = lunar.getChunkSource().getGenerator();
        String noise = generator instanceof NoiseBasedChunkGenerator noiseBased
                ? noiseBased.generatorSettings().unwrapKey().map(key -> key.identifier().toString()).orElse("inline")
                : generator.getClass().getSimpleName();
        helper.assertTrue(noise.equals(LUNAR_NOISE.toString()), String.format(OWN_NOISE, noise));
        for (BlockPos spot : TEST_SPOTS) {
            assertColumn(helper, lunar, spot);
        }
        helper.succeed();
    }

    private static void assertColumn(GameTestHelper helper, ServerLevel lunar, BlockPos spot) {
        lunar.getChunk(spot.getX() >> 4, spot.getZ() >> 4);
        int surfaceY = lunar.getHeight(Heightmap.Types.WORLD_SURFACE, spot.getX(), spot.getZ()) - 1;
        BlockState surface = lunar.getBlockState(spot.atY(surfaceY));
        helper.assertTrue(surface.is(GooBlocks.REGOLITH.get()), String.format(SURFACE, spot, surfaceY, surface));
        int y = surfaceY;
        while (y > MOLTEN_IRON_TOP_Y && lunar.getBlockState(spot.atY(y)).is(GooBlocks.REGOLITH.get())) {
            y--;
        }
        BlockState beneath = lunar.getBlockState(spot.atY(y));
        helper.assertTrue(beneath.is(GooBlocks.ANORTHOSITE.get()) || beneath.is(Blocks.BASALT),
                String.format(BELOW_REGOLITH, spot, y, beneath));
        for (int depth = IRON_CORE_TOP_Y + 1; depth <= MOLTEN_IRON_TOP_Y; depth++) {
            assertStratum(helper, lunar, spot.atY(depth), GooBlocks.MOLTEN_IRON.get().defaultBlockState());
        }
        for (int depth = FLOOR_Y + 1; depth <= IRON_CORE_TOP_Y; depth++) {
            assertStratum(helper, lunar, spot.atY(depth), GooBlocks.IRON_CORE.get().defaultBlockState());
        }
        assertStratum(helper, lunar, spot.atY(FLOOR_Y), Blocks.BEDROCK.defaultBlockState());
    }

    private static void assertStratum(GameTestHelper helper, ServerLevel lunar, BlockPos pos, BlockState expected) {
        BlockState found = lunar.getBlockState(pos);
        helper.assertTrue(found.is(expected.getBlock()),
                String.format(STRATUM, pos.atY(0), expected.getBlock(), pos.getY(), found));
    }
}
