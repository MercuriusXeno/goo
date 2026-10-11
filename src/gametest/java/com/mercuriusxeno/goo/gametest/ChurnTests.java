package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.program.ChurnMap;
import com.mercuriusxeno.goo.ability.world.AbilityImpact;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import java.util.ArrayList;
import java.util.List;

/**
 * Gametest for Deep's churn: a column whose core is deepslate and whose four
 * strips are planks, churned to its full depth, ends with planks in the core
 * and deepslate in the strips (decision churn-rotates-a-plus-shaped-column).
 * The column floats above the test's own bay.
 */
public final class ChurnTests {

    private static final Identifier DEEP_CHURN = Identifier.parse("goo:deep_churn");
    /** How far above the bay's floor the column's top layer floats. */
    private static final int LIFT = 12;
    /** The dragged size the test casts at; deep_churn.json churns four layers a block of size. */
    private static final double SIZE = 1;
    private static final int DEPTH = 4;
    private static final Block CORE = Blocks.DEEPSLATE;
    private static final Block STRIP = Blocks.OAK_PLANKS;
    private static final String ABILITY_REQUIRED = "goo:deep_churn must be loaded";
    private static final String SHOULD_SWAP = "%s should hold %s after the churn, holds %s";

    private ChurnTests() {
    }

    /**
     * A deepslate core inside plank strips, churned four layers deep, ends
     * with plank in every core cell and deepslate in every strip cell.
     *
     * @param helper the gametest helper
     */
    public static void churnSwapsTheLayers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        AbilityDefinition churn = AbilityRegistry.of(level).getAbility(DEEP_CHURN);
        helper.assertTrue(churn != null, ABILITY_REQUIRED);
        BlockPos origin = columnOrigin(helper);
        List<BlockPos> core = new ArrayList<>();
        List<BlockPos> strips = new ArrayList<>();
        for (int index = 0; index < ChurnMap.CELLS; index++) {
            int[] coreCell = ChurnMap.coreCell(index);
            int[] stripCell = ChurnMap.stripCellOf(index);
            for (int down = 0; down < DEPTH; down++) {
                core.add(origin.offset(coreCell[0], -down, coreCell[1]));
                strips.add(origin.offset(stripCell[0], -down, stripCell[1]));
            }
        }
        core.forEach(pos -> level.setBlockAndUpdate(pos, CORE.defaultBlockState()));
        strips.forEach(pos -> level.setBlockAndUpdate(pos, STRIP.defaultBlockState()));
        BlockPos landed = origin.offset(1, 0, 1);

        AbilityImpact.land(level, landed, GooTypes.DEEP, Direction.UP, churn, null, SIZE);

        helper.succeedWhen(() -> {
            assertHolds(helper, core, STRIP);
            assertHolds(helper, strips, CORE);
            core.forEach(pos -> level.removeBlock(pos, false));
            strips.forEach(pos -> level.removeBlock(pos, false));
            level.removeBlock(landed.above(), false);
        });
    }

    private static void assertHolds(GameTestHelper helper, List<BlockPos> cells, Block block) {
        for (BlockPos pos : cells) {
            Block holds = helper.getLevel().getBlockState(pos).getBlock();
            helper.assertTrue(holds == block, String.format(SHOULD_SWAP, pos, block, holds));
        }
    }

    /**
     * The north-west core cell of the column's top layer, floating in the bay.
     *
     * @param helper the gametest helper
     * @return the cell's absolute position
     */
    private static BlockPos columnOrigin(GameTestHelper helper) {
        return helper.absolutePos(new BlockPos(2, LIFT, 2));
    }
}
