package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.ChargeSize;
import com.mercuriusxeno.goo.network.BlastShardsPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Gametest that a Blast breaking a floor of stone and dirt sends the players
 * near it the cells it broke, each with the block that stood there, so each
 * client throws that block's own shards where it stood.
 * blast-broken-blocks-throw-their-own-break-shards
 */
@RegistersGameTests
public final class BlastShardsTests {
    private static final String BLAST_NAMES_EACH_BROKEN_BLOCK = "blast_names_each_broken_block";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, BLAST_NAMES_EACH_BROKEN_BLOCK,
                BlastShardsTests::blastNamesEachBrokenBlock);
    }

    private static final int FLOOR_SPAN = 16;
    private static final int FLOOR_TOP = 2;
    private static final int BAY_HEIGHT = 5;
    private static final String ONE_SHARDS_PAYLOAD = "The viewer should receive one shards payload, received %d";
    private static final String NAMES_A_CELL = "The shards payload should name a broken cell";
    private static final String CELL_BROKEN = "The shards payload names %s, which should read air, reads %s";
    private static final String STATE_LAID = "The shards payload carries %s at %s, where %s was laid";

    private BlastShardsTests() {
    }

    /**
     * Blast cast into a checkered floor of stone and dirt sends the viewer
     * one shards payload naming broken cells, each air after the cast and
     * each carrying the block laid there.
     *
     * @param helper the gametest helper
     */
    public static void blastNamesEachBrokenBlock(GameTestHelper helper) {
        Map<BlockPos, Block> laid = checkeredFloor(helper);
        PayloadKindsRecorder viewer = ExplosionLookTests.standViewer(helper);

        ExplosionLookTests.castBlast(helper, ChargeSize.REFERENCE_RADIUS);

        List<BlastShardsPayload> payloads = ExplosionLookTests.shardsOfTheStruckBlock(helper, viewer);
        helper.assertTrue(payloads.size() == 1, String.format(ONE_SHARDS_PAYLOAD, payloads.size()));
        List<BlastShardsPayload.Shattered> cells = payloads.getFirst().cells();
        helper.assertFalse(cells.isEmpty(), NAMES_A_CELL);
        // a bay the run rotates reads back wrong through relativePos, so each cell compares absolute
        for (BlastShardsPayload.Shattered cell : cells) {
            BlockState standing = helper.getLevel().getBlockState(cell.pos());
            helper.assertTrue(standing.isAir(), String.format(CELL_BROKEN, cell.pos(), standing));
            Block laidThere = laid.get(cell.pos());
            helper.assertTrue(laidThere != null && cell.state().is(laidThere),
                    String.format(STATE_LAID, cell.state(), cell.pos(), laidThere));
        }
        ExplosionLookTests.removeViewer(helper, viewer);
        helper.succeed();
    }

    private static Block checkered(BlockPos cell) {
        return (cell.getX() + cell.getY() + cell.getZ()) % 2 == 0 ? Blocks.STONE : Blocks.DIRT;
    }

    /**
     * Lays a checkered floor of stone and dirt under open air across the bay.
     *
     * @param helper the gametest helper
     * @return each floor block laid, by its absolute position
     */
    private static Map<BlockPos, Block> checkeredFloor(GameTestHelper helper) {
        Map<BlockPos, Block> laid = new HashMap<>();
        BlockPos.betweenClosed(0, 0, 0, FLOOR_SPAN - 1, BAY_HEIGHT - 1, FLOOR_SPAN - 1).forEach(cell -> {
            Block block = cell.getY() <= FLOOR_TOP ? checkered(cell) : Blocks.AIR;
            helper.setBlock(cell, block);
            laid.put(helper.absolutePos(cell), block);
        });
        return laid;
    }
}
