package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.slf4j.Logger;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The pour's cost harness: one fixed mound of a runny goo poured to rest on
 * a walled floor, its cost logged as three counts under one line prefix so a
 * later run reads them back beside these.
 * goo-pour-cost-is-measured-before-and-after-the-network
 */
@RegistersGameTests
public final class PourCostTests {

    /** The prefix the harness's line carries, which a later run's line is read back beside. */
    static final String COST_LINE = "[goo pour cost]";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String SLICK_MOUND_POURS_TO_REST = "slick_mound_pours_to_rest_on_a_walled_floor";
    /** The walled floor's inside runs from this column and row of test_bay to the next constant's. */
    private static final int INSIDE_FROM = 1;
    /** The walled floor's last inside column and row, so the inside stands four cells by four. */
    private static final int INSIDE_TO = 4;
    /** The row of cells the goo stands in, over the stone floor at row 0. */
    private static final int CELL_ROW = 1;
    /** The mound's four full cells, the middle of the walled floor's inside. */
    private static final List<BlockPos> MOUND = List.of(new BlockPos(2, CELL_ROW, 2), new BlockPos(3, CELL_ROW, 2),
            new BlockPos(2, CELL_ROW, 3), new BlockPos(3, CELL_ROW, 3));
    /** The layers a whole block of goo holds. */
    private static final int WHOLE_BLOCK_LAYERS = 15;
    /**
     * Ticks with no volume cell written that stand for rest: far past Kinetic's pour pace of one tick, so a cell
     * still pouring writes within it.
     */
    private static final int REST_TICKS = 20;
    private static final String STILL_POURING = "The slick mound should stand at rest";
    private static final String LAYERS_LOST = "The walled floor should hold the mound's %d layers at rest, holds %d";

    private PourCostTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, SLICK_MOUND_POURS_TO_REST,
                PourCostTests::slickMoundPoursToRestOnAWalledFloor);
    }

    /**
     * Four full cells of Kinetic's slick, a runny goo pouring every tick, in
     * the middle of a stone floor walled four cells by four, pour until no
     * cell is written for a stretch; the floor then holds the mound's layers,
     * and the harness logs the volume cell writes, the pour ticks and the
     * server ticks from the first pour to rest.
     * goo-pour-cost-is-measured-before-and-after-the-network
     * spread-decreases-the-cell-it-leaves
     *
     * @param helper the gametest helper
     */
    public static void slickMoundPoursToRestOnAWalledFloor(GameTestHelper helper) {
        layWalledFloor(helper);
        for (BlockPos cell : MOUND) {
            helper.setBlock(cell, GooBlocks.KINETIC_SLICK.get().defaultBlockState()
                    .setValue(VolumeCellBlock.LAYERS, WHOLE_BLOCK_LAYERS));
        }
        PourCost cost = PourCost.watch(helper);
        int poured = MOUND.size() * WHOLE_BLOCK_LAYERS;
        helper.succeedWhen(() -> {
            helper.assertTrue(cost.restedFor(REST_TICKS), STILL_POURING);
            int held = OpenFloor.layersIn(helper, inside()).stream().mapToInt(Integer::intValue).sum();
            helper.assertTrue(held == poured, String.format(LAYERS_LOST, poured, held));
            cost.stop();
            LOGGER.info("{} {}: writes={} pourTicks={} serverTicks={}", COST_LINE, SLICK_MOUND_POURS_TO_REST,
                    cost.writes(), cost.pourTicks(), cost.ticksToRest());
        });
    }

    /**
     * Lays stone under the whole bay, a stone wall around the inside, and air over the inside.
     *
     * @param helper the gametest helper
     */
    private static void layWalledFloor(GameTestHelper helper) {
        for (int east = INSIDE_FROM - 1; east <= INSIDE_TO + 1; east++) {
            for (int south = INSIDE_FROM - 1; south <= INSIDE_TO + 1; south++) {
                BlockPos cell = new BlockPos(east, CELL_ROW, south);
                helper.setBlock(cell.below(), Blocks.STONE);
                boolean wall = east < INSIDE_FROM || east > INSIDE_TO || south < INSIDE_FROM || south > INSIDE_TO;
                helper.setBlock(cell, wall ? Blocks.STONE : Blocks.AIR);
                helper.setBlock(cell.above(), Blocks.AIR);
            }
        }
    }

    private static List<BlockPos> inside() {
        List<BlockPos> inside = new ArrayList<>();
        for (int east = INSIDE_FROM; east <= INSIDE_TO; east++) {
            for (int south = INSIDE_FROM; south <= INSIDE_TO; south++) {
                inside.add(new BlockPos(east, CELL_ROW, south));
            }
        }
        return inside;
    }
}
