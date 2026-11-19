package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.program.MyceliumPaths;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.slf4j.Logger;
import java.util.List;
import java.util.function.Consumer;

/**
 * The connectivity question's cost harness: one fixed sculk serpentine laid
 * across the bay, its two ends asked whether they join a fixed number of
 * times through the seam Transmit asks, and the mean wall-clock time per
 * question logged under one line prefix, so the run on the stored mycelium
 * network reads beside the run on the direct search.
 * mycelium-networks-are-cached-dags-joined-by-chunk-connectors
 */
@RegistersGameTests
public final class MyceliumPathCostTests {

    /** The prefix the harness's line carries, which a later run's line is read back beside. */
    static final String COST_LINE = "[goo mycelium path cost]";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String SERPENTINE_ENDS_JOIN = "mycelium_path_cost_on_a_serpentine";
    private static final int BAY_SPAN = 16;
    private static final int FLOOR_Y = 0;
    private static final int LINE_Y = 1;
    /** Transmit's range, which the seam bounds its answer by. */
    private static final double RANGE = 64;
    /** Questions asked before the timing, so the timed ones run on compiled code. */
    private static final int WARM_UP = 200;
    /** Questions timed. */
    private static final int QUESTIONS = 2000;
    private static final double NANOS_PER_MICRO = 1000.0;
    private static final String SHOULD_JOIN = "The serpentine's two ends should read connected";

    private MyceliumPathCostTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, SERPENTINE_ENDS_JOIN, MyceliumPathCostTests::serpentineEndsJoin);
    }

    /**
     * Lays a sculk serpentine over a stone floor: every even row whole, each
     * odd row a single block at alternate ends, so the path between the two
     * ends runs the whole serpentine. Asks whether the two ends join, and
     * logs the mean time per question.
     *
     * @param helper the gametest helper
     */
    public static void serpentineEndsJoin(GameTestHelper helper) {
        BlockPos.betweenClosed(0, FLOOR_Y, 0, BAY_SPAN - 1, FLOOR_Y, BAY_SPAN - 1)
                .forEach(cell -> helper.setBlock(cell.immutable(), Blocks.STONE));
        int blocks = 0;
        for (int z = 0; z < BAY_SPAN; z++) {
            for (int x = 0; x < BAY_SPAN; x++) {
                if (inSerpentine(x, z)) {
                    helper.setBlock(new BlockPos(x, LINE_Y, z), Blocks.SCULK);
                    blocks++;
                }
            }
        }
        List<BlockPos> start = List.of(helper.absolutePos(new BlockPos(0, LINE_Y, 0)));
        BlockPos end = helper.absolutePos(new BlockPos(0, LINE_Y, BAY_SPAN - 2));

        boolean joined = true;
        for (int asked = 0; asked < WARM_UP; asked++) {
            joined &= MyceliumPaths.connects(helper.getLevel(), start, end, RANGE);
        }
        long began = System.nanoTime();
        for (int asked = 0; asked < QUESTIONS; asked++) {
            joined &= MyceliumPaths.connects(helper.getLevel(), start, end, RANGE);
        }
        double meanMicros = (System.nanoTime() - began) / NANOS_PER_MICRO / QUESTIONS;

        LOGGER.info("{} {}: meanMicrosPerQuestion={} questions={} serpentineBlocks={}", COST_LINE,
                SERPENTINE_ENDS_JOIN, String.format("%.2f", meanMicros), QUESTIONS, blocks);
        helper.assertTrue(joined, SHOULD_JOIN);
        helper.succeed();
    }

    /**
     * Whether a cell of the floor's top row holds the serpentine: the even
     * rows whole but the last, and each odd row one block at the east end
     * then the west, alternately.
     */
    private static boolean inSerpentine(int x, int z) {
        if (z == BAY_SPAN - 1) {
            return false;
        }
        if (z % 2 == 0) {
            return true;
        }
        return (z / 2) % 2 == 0 ? x == BAY_SPAN - 1 : x == 0;
    }
}
