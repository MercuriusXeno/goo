package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.lunar.Lunar;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for lunar's crystal and metal goo pockets: generating a square of chunks around a spot deep in the
 * highlands lays crystal goo's Shatter cells on its surface, and one around a spot deep in maria lays metal goo's
 * Spikes cells.
 * decision crystal-and-metal-goo-spawn-in-the-highlands-and-maria
 */
@RegistersGameTests
public final class LunarResidualPocketTests {
    private static final String LUNAR_HIGHLANDS_AND_MARIA_LAY_THEIR_RESIDUALS =
            "lunar_highlands_and_maria_lay_their_residuals";

    /** The square of chunks generated reaches this many chunks each way of the spot's own. */
    private static final int CHUNK_REACH = 4;
    /** A pocket's cells are sought this far under the surface's top, past cells that levelled down a step. */
    private static final int SURFACE_DEPTH = 3;
    private static final int CHUNK_SIDE = 16;

    private static final String LUNAR_REQUIRED = "The server should hold goo:lunar";
    private static final String NO_SPOT = "No spot within the search lies deep in %s";
    private static final String NO_RESIDUAL = "The %d chunks around %s deep in %s should hold a %s cell on their"
            + " surface, find none";

    private LunarResidualPocketTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, LUNAR_HIGHLANDS_AND_MARIA_LAY_THEIR_RESIDUALS,
                LunarResidualPocketTests::lunarHighlandsAndMariaLayTheirResiduals);
    }

    /**
     * Generates the chunks around a spot deep in the highlands and finds a crystal Shatter cell on their surface, then
     * around a spot deep in maria and finds a metal Spikes cell.
     *
     * @param helper the gametest helper
     */
    public static void lunarHighlandsAndMariaLayTheirResiduals(GameTestHelper helper) {
        ServerLevel lunar = helper.getLevel().getServer().getLevel(Lunar.LEVEL);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
        assertResidualAround(helper, lunar, LunarSurfaceBiomes.HIGHLANDS, GooBlocks.CRYSTAL_SHATTER.get());
        assertResidualAround(helper, lunar, LunarSurfaceBiomes.MARIA, GooBlocks.METAL_SPIKES.get());
        helper.succeed();
    }

    private static void assertResidualAround(GameTestHelper helper, ServerLevel lunar, ResourceKey<Biome> biome,
                                             Block residual) {
        BlockPos spot = LunarSurfaceBiomes.spotIn(lunar, biome);
        helper.assertTrue(spot != null, String.format(NO_SPOT, biome.identifier()));
        int side = 2 * CHUNK_REACH + 1;
        helper.assertTrue(holdsOnSurface(lunar, spot, residual),
                String.format(NO_RESIDUAL, side * side, spot, biome.identifier(), residual));
    }

    /**
     * Whether any chunk of the square around a spot holds a block on its surface, at the top or a few blocks under it.
     *
     * @param lunar    goo:lunar
     * @param spot     the square's middle
     * @param residual the block sought
     * @return true where a column holds it
     */
    private static boolean holdsOnSurface(ServerLevel lunar, BlockPos spot, Block residual) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int cx = (spot.getX() >> 4) - CHUNK_REACH; cx <= (spot.getX() >> 4) + CHUNK_REACH; cx++) {
            for (int cz = (spot.getZ() >> 4) - CHUNK_REACH; cz <= (spot.getZ() >> 4) + CHUNK_REACH; cz++) {
                LevelChunk chunk = lunar.getChunk(cx, cz);
                for (int x = 0; x < CHUNK_SIDE; x++) {
                    for (int z = 0; z < CHUNK_SIDE; z++) {
                        int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                        for (int y = top; y >= top - SURFACE_DEPTH; y--) {
                            if (chunk.getBlockState(at.set(chunk.getPos().getMinBlockX() + x, y,
                                    chunk.getPos().getMinBlockZ() + z)).is(residual)) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }
}
