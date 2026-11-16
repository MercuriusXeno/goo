package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * surface over anorthosite or basalt, molten iron over the iron core at depth, and bedrock at the floor; and its
 * ground holds meteoric iron veined through the anorthosite.
 * decision lunar-generates-from-its-own-noise-and-surface-rules
 * decision lunar-matter-is-anorthosite-basalt-regolith-and-an-iron-core
 * decision lunar-ground-gains-meteoric-iron-impact-glass-breccia-and-crater-ice
 */
@RegistersGameTests
public final class LunarTerrainTests {
    private static final String LUNAR_COLUMN_READS_ITS_STRATA = "lunar_column_reads_its_strata";
    private static final String LUNAR_GROUND_HOLDS_METEORIC_IRON = "lunar_ground_holds_meteoric_iron";

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
    private static final String METEORIC_IRON_IN_ANORTHOSITE =
            "The chunks around %s should hold meteoric iron touching anorthosite";
    /** The chunks one step around a test spot's own, which a vein search scans. */
    private static final int CHUNK_REACH = 1;
    private static final int CHUNK_SIDE = 16;
    /** The tallest the ground stands, which a vein search reads up to. */
    private static final int GROUND_TOP_Y = 200;

    private LunarTerrainTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, LUNAR_COLUMN_READS_ITS_STRATA, LunarTerrainTests::lunarColumnReadsItsStrata);
        GooTestFunctions.registerTest(registrar, LUNAR_GROUND_HOLDS_METEORIC_IRON,
                LunarTerrainTests::lunarGroundHoldsMeteoricIron);
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

    /**
     * The chunks around each test spot in goo:lunar hold meteoric iron touching anorthosite: the veins run through
     * lunar's highland stone.
     *
     * @param helper the gametest helper
     */
    public static void lunarGroundHoldsMeteoricIron(GameTestHelper helper) {
        ServerLevel lunar = helper.getLevel().getServer().getLevel(LUNAR);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
        for (BlockPos spot : TEST_SPOTS) {
            helper.assertTrue(holdsMeteoricIronInAnorthosite(lunar, spot), String.format(METEORIC_IRON_IN_ANORTHOSITE, spot));
        }
        helper.succeed();
    }

    private static boolean holdsMeteoricIronInAnorthosite(ServerLevel lunar, BlockPos spot) {
        int spotChunkX = spot.getX() >> 4;
        int spotChunkZ = spot.getZ() >> 4;
        for (int chunkX = spotChunkX - CHUNK_REACH; chunkX <= spotChunkX + CHUNK_REACH; chunkX++) {
            for (int chunkZ = spotChunkZ - CHUNK_REACH; chunkZ <= spotChunkZ + CHUNK_REACH; chunkZ++) {
                lunar.getChunk(chunkX, chunkZ);
                if (chunkHoldsMeteoricIronInAnorthosite(lunar, chunkX, chunkZ)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean chunkHoldsMeteoricIronInAnorthosite(ServerLevel lunar, int chunkX, int chunkZ) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < CHUNK_SIDE; x++) {
            for (int z = 0; z < CHUNK_SIDE; z++) {
                for (int y = MOLTEN_IRON_TOP_Y + 1; y <= GROUND_TOP_Y; y++) {
                    pos.set(chunkX * CHUNK_SIDE + x, y, chunkZ * CHUNK_SIDE + z);
                    if (lunar.getBlockState(pos).is(GooBlocks.METEORIC_IRON.get()) && touchesAnorthosite(lunar, pos)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean touchesAnorthosite(ServerLevel lunar, BlockPos pos) {
        for (Direction side : Direction.values()) {
            if (lunar.getBlockState(pos.relative(side)).is(GooBlocks.ANORTHOSITE.get())) {
                return true;
            }
        }
        return false;
    }
}
