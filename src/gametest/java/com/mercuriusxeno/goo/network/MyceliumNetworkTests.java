package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.program.MyceliumPaths;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.List;
import java.util.function.Consumer;

/**
 * Gametests for the stored mycelium network through the connectivity seam
 * Transmit asks: a sculk line across a chunk border reads connected end to
 * end, and a line broken then mended reads unconnected then connected, so the
 * stored network follows the world.
 * mycelium-networks-are-cached-dags-joined-by-chunk-connectors
 */
@RegistersGameTests
public final class MyceliumNetworkTests {
    private static final String LINE_ACROSS_A_CHUNK_BORDER = "mycelium_line_across_a_chunk_border_connects";
    private static final String BROKEN_LINE_FOLLOWS_THE_WORLD = "mycelium_broken_line_follows_the_world";

    /** march_bay's length along x, past two chunks, so a line along it always crosses a chunk border. */
    private static final int MARCH_BAY_LENGTH = 40;
    private static final int LIGHT_BAY_SPAN = 16;
    private static final int LINE_Y = 1;
    private static final int LINE_Z = 3;
    private static final double RANGE = 64;
    private static final BlockPos BREAK_AT = new BlockPos(7, LINE_Y, LINE_Z);
    private static final String SHOULD_CROSS = "The line's ends should sit in different chunks, sit in %s and %s";
    private static final String SHOULD_CONNECT = "The line's two ends should read connected";
    private static final String SHOULD_PART = "The broken line's two ends should read unconnected";
    private static final String SHOULD_MEND = "The mended line's two ends should read connected";

    private MyceliumNetworkTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, LINE_ACROSS_A_CHUNK_BORDER,
                MyceliumNetworkTests::lineAcrossAChunkBorderConnects);
        GooTestFunctions.registerTest(registrar, BROKEN_LINE_FOLLOWS_THE_WORLD,
                MyceliumNetworkTests::brokenLineFollowsTheWorld);
    }

    /**
     * Mycelium networks connect through chunks through chunk connectors: an
     * unbroken sculk line the length of march_bay, its ends in different
     * chunks, reads connected end to end.
     *
     * @param helper the gametest helper
     */
    public static void lineAcrossAChunkBorderConnects(GameTestHelper helper) {
        layLine(helper, MARCH_BAY_LENGTH);
        BlockPos west = helper.absolutePos(new BlockPos(0, LINE_Y, LINE_Z));
        BlockPos east = helper.absolutePos(new BlockPos(MARCH_BAY_LENGTH - 1, LINE_Y, LINE_Z));
        helper.assertTrue(!ChunkPos.containing(west).equals(ChunkPos.containing(east)),
                String.format(SHOULD_CROSS, ChunkPos.containing(west), ChunkPos.containing(east)));

        helper.assertTrue(MyceliumPaths.connects(helper.getLevel(), List.of(west), east, RANGE), SHOULD_CONNECT);
        helper.succeed();
    }

    /**
     * The stored network follows the world: asked once on an unbroken line,
     * then with one block broken out of it the ends read unconnected, and with
     * sculk placed back they read connected.
     *
     * @param helper the gametest helper
     */
    public static void brokenLineFollowsTheWorld(GameTestHelper helper) {
        layLine(helper, LIGHT_BAY_SPAN);
        List<BlockPos> west = List.of(helper.absolutePos(new BlockPos(0, LINE_Y, LINE_Z)));
        BlockPos east = helper.absolutePos(new BlockPos(LIGHT_BAY_SPAN - 1, LINE_Y, LINE_Z));
        boolean unbroken = MyceliumPaths.connects(helper.getLevel(), west, east, RANGE);

        helper.setBlock(BREAK_AT, Blocks.AIR);
        boolean broken = MyceliumPaths.connects(helper.getLevel(), west, east, RANGE);
        helper.setBlock(BREAK_AT, Blocks.SCULK);
        boolean mended = MyceliumPaths.connects(helper.getLevel(), west, east, RANGE);

        helper.assertTrue(unbroken, SHOULD_CONNECT);
        helper.assertTrue(!broken, SHOULD_PART);
        helper.assertTrue(mended, SHOULD_MEND);
        helper.succeed();
    }

    /**
     * Lays a stone floor under a sculk line along x at the bay's third row.
     *
     * @param helper the gametest helper
     * @param length how many blocks long the line runs
     */
    private static void layLine(GameTestHelper helper, int length) {
        for (int x = 0; x < length; x++) {
            helper.setBlock(new BlockPos(x, LINE_Y - 1, LINE_Z), Blocks.STONE);
            helper.setBlock(new BlockPos(x, LINE_Y, LINE_Z), Blocks.SCULK);
        }
    }
}
