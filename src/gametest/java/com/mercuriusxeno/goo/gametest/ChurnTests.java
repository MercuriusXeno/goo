package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.world.AbilityImpact;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Gametests for Deep's churn on a floating patch of stone over an iron vein:
 * the vein rises through the stone to the patch's top and the stone sinks
 * into the room it leaves, and a second cast on the same spot moves blocks
 * without making or losing any (decision churn-pulls-a-vein-up-as-a-fluid-chain).
 * The patch floats above the test's own bay.
 */
@RegistersGameTests
public final class ChurnTests {
    private static final String CHURN_PULLS_THE_VEIN = "churn_pulls_the_vein_up";
    private static final String CHURN_TWICE_CONSERVES = "churn_twice_conserves_the_blocks";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, CHURN_PULLS_THE_VEIN, ChurnTests::churnPullsTheVeinUp);
        GooTestFunctions.registerTest(registrar, CHURN_TWICE_CONSERVES, ChurnTests::churnTwiceConservesTheBlocks);
    }

    private static final Identifier DEEP_CHURN = Identifier.parse("goo:deep_churn");
    /** How far above the bay's floor the patch's top layer floats. */
    private static final int LIFT = 12;
    /** The dragged size the test casts at. */
    private static final double SIZE = 1;
    /** The patch's width on each horizontal axis, wide enough to hold any churn a size-one cast reaches. */
    private static final int WIDTH = 7;
    /** The patch's layers, its top layer among them. */
    private static final int LAYERS = 6;
    /** The struck block's offset from the patch's north-west top corner on each horizontal axis. */
    private static final int MIDDLE = 3;
    /** The vein's cells, offset from the struck block: two in the struck column, one beside its foot. */
    private static final List<BlockPos> VEIN = List.of(
            new BlockPos(0, -3, 0), new BlockPos(0, -4, 0), new BlockPos(1, -4, 0));
    /** Where each vein cell stands once the vein has risen to the top, in VEIN's order. */
    private static final List<BlockPos> RISEN = List.of(
            new BlockPos(0, -1, 0), new BlockPos(0, 0, 0), new BlockPos(1, 0, 0));
    /** The stone that stood over the vein, which sinks to the vein's old foot. */
    private static final List<BlockPos> SUNK = List.of(new BlockPos(0, -4, 0), new BlockPos(1, -4, 0));
    private static final Block HOST = Blocks.STONE;
    private static final Block MINERAL = Blocks.IRON_ORE;
    /** Air rows the counted box holds above the patch and below it, so any block a churn spills is counted. */
    private static final int MARGIN = 2;
    private static final String ABILITY_REQUIRED = "goo:deep_churn must be loaded";
    private static final String SHOULD_END = "The churn's marker should go once the vein stops rising";
    private static final String SHOULD_HOLD = "%s should hold %s after the churn, holds %s";
    private static final String SHOULD_CONSERVE = "Two churns should leave the patch's blocks as counted before, %s, counted %s";

    private ChurnTests() {
    }

    /**
     * An iron vein under stone, churned, stands at the patch's top with the
     * stone that was over it sunk to its old foot.
     *
     * @param helper the gametest helper
     */
    public static void churnPullsTheVeinUp(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        AbilityDefinition churn = churnAbility(helper);
        BlockPos struck = buildPatch(helper);

        AbilityImpact.land(level, struck, GooTypes.DEEP, Direction.UP, churn, null, SIZE);

        helper.succeedWhen(() -> {
            for (BlockPos risen : RISEN) {
                assertHolds(helper, struck.offset(risen), MINERAL);
            }
            for (BlockPos sunk : SUNK) {
                assertHolds(helper, struck.offset(sunk), HOST);
            }
            helper.assertFalse(isMarker(level, struck.above()), SHOULD_END);
            clearPatch(helper, struck);
        });
    }

    /**
     * Two churns cast on the same spot of a meadow over the vein, the second
     * the tick after the first, leave the counted box holding the blocks it held before the
     * first, each kind in the same number.
     *
     * @param helper the gametest helper
     */
    public static void churnTwiceConservesTheBlocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        AbilityDefinition churn = churnAbility(helper);
        BlockPos struck = buildPatch(helper);
        sowMeadow(helper, struck);
        Map<Block, Integer> before = countBlocks(level, struck);

        helper.startSequence()
                .thenExecute(() -> AbilityImpact.land(level, struck, GooTypes.DEEP, Direction.UP, churn, null, SIZE))
                .thenIdle(1)
                .thenExecute(() -> AbilityImpact.land(level, struck, GooTypes.DEEP, Direction.UP, churn, null, SIZE))
                .thenIdle(1)
                .thenWaitUntil(() -> helper.assertFalse(anyMarker(level, struck), SHOULD_END))
                .thenExecute(() -> {
                    Map<Block, Integer> after = countBlocks(level, struck);
                    clearPatch(helper, struck);
                    helper.assertTrue(before.equals(after), String.format(SHOULD_CONSERVE, before, after));
                })
                .thenSucceed();
    }

    private static AbilityDefinition churnAbility(GameTestHelper helper) {
        AbilityDefinition churn = AbilityRegistry.of(helper.getLevel()).getAbility(DEEP_CHURN);
        helper.assertTrue(churn != null, ABILITY_REQUIRED);
        return churn;
    }

    private static boolean anyMarker(ServerLevel level, BlockPos struck) {
        for (BlockPos cell : countedCells(struck)) {
            if (isMarker(level, cell)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isMarker(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).is(GooBlocks.ABILITY_BLOCK.get());
    }

    /**
     * Fills the floating patch with stone and lays the iron vein in it.
     *
     * @param helper the gametest helper
     * @return the struck block, the middle of the patch's top layer
     */
    private static BlockPos buildPatch(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos struck = helper.absolutePos(new BlockPos(MIDDLE + 1, LIFT, MIDDLE + 1));
        for (BlockPos cell : patchCells(struck)) {
            level.setBlockAndUpdate(cell, HOST.defaultBlockState());
        }
        for (BlockPos vein : VEIN) {
            level.setBlockAndUpdate(struck.offset(vein), MINERAL.defaultBlockState());
        }
        return struck;
    }

    /**
     * Tops the patch with grass over a layer of sand and plants short grass
     * on every other cell, the struck cell's four neighbors among them, so a
     * churn that moved the ground under a plant would pop it.
     *
     * @param helper the gametest helper
     * @param struck the struck block
     */
    private static void sowMeadow(GameTestHelper helper, BlockPos struck) {
        ServerLevel level = helper.getLevel();
        for (int dx = -MIDDLE; dx <= MIDDLE; dx++) {
            for (int dz = -MIDDLE; dz <= MIDDLE; dz++) {
                BlockPos cell = struck.offset(dx, 0, dz);
                level.setBlockAndUpdate(cell, Blocks.GRASS_BLOCK.defaultBlockState());
                level.setBlockAndUpdate(cell.below(), Blocks.SAND.defaultBlockState());
                if (Math.floorMod(dx + dz, 2) == 1) {
                    level.setBlockAndUpdate(cell.above(), Blocks.SHORT_GRASS.defaultBlockState());
                }
            }
        }
    }

    private static Iterable<BlockPos> patchCells(BlockPos struck) {
        return BlockPos.betweenClosed(struck.offset(-MIDDLE, 1 - LAYERS, -MIDDLE),
                struck.offset(WIDTH - 1 - MIDDLE, 0, WIDTH - 1 - MIDDLE));
    }

    private static Iterable<BlockPos> countedCells(BlockPos struck) {
        return BlockPos.betweenClosed(struck.offset(-MIDDLE - MARGIN, 1 - LAYERS - MARGIN, -MIDDLE - MARGIN),
                struck.offset(WIDTH - 1 - MIDDLE + MARGIN, MARGIN, WIDTH - 1 - MIDDLE + MARGIN));
    }

    private static Map<Block, Integer> countBlocks(ServerLevel level, BlockPos struck) {
        Map<Block, Integer> counts = new HashMap<>();
        for (BlockPos cell : countedCells(struck)) {
            counts.merge(level.getBlockState(cell).getBlock(), 1, Integer::sum);
        }
        return counts;
    }

    private static void clearPatch(GameTestHelper helper, BlockPos struck) {
        for (BlockPos cell : countedCells(struck)) {
            helper.getLevel().setBlock(cell, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private static void assertHolds(GameTestHelper helper, BlockPos pos, Block block) {
        BlockState holds = helper.getLevel().getBlockState(pos);
        helper.assertTrue(holds.is(block), String.format(SHOULD_HOLD, pos, block, holds.getBlock()));
    }
}
