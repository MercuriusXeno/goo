package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.world.AbilityImpact;
import com.mercuriusxeno.goo.block.ability.PrismBlock;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Gametests for Leaf Verdant: leaf goo landing on a prism makes it verdant,
 * and as it pulses, cobblestone around it turns mossy, open water gains a
 * lily pad, and sugar cane in its reach is nudged along.
 * verdant-prism-greens-blocks-slowly
 */
public final class VerdantTests {

    private static final Identifier VERDANT = Identifier.fromNamespaceAndPath(Goo.MODID, "leaf_verdant");
    private static final String ABILITY_REQUIRED = "Ability registry must hold leaf_verdant";
    private static final int BAY_EDGE = 5;
    /** The rows of the bay floor laid as cobblestone; the rows past them hold water. */
    private static final int COBBLE_ROWS_END = 2;
    private static final BlockPos PRISM = new BlockPos(2, 1, 1);
    /** Sand at the edge of the cobble rows, beside the water, the cane standing on it. */
    private static final BlockPos SAND = new BlockPos(BAY_EDGE, 0, COBBLE_ROWS_END);
    private static final BlockPos CANE = SAND.above();
    private static final String SHOULD_GREEN = "Verdant should mossy a cobblestone, lily the water and nudge the cane;"
            + " mossy %b, lily %b, cane %s";

    private VerdantTests() {
    }

    /**
     * Lays a floor half cobblestone, half open water, with sugar cane at the
     * seam, stands a prism on it and lands leaf goo on the prism; before the
     * bound runs out a cobblestone stands mossy, a lily pad floats on the
     * water, and the cane has aged or grown.
     *
     * @param helper the gametest helper
     */
    public static void verdantGreensCobbleAndWater(GameTestHelper helper) {
        for (int x = 0; x <= BAY_EDGE; x++) {
            for (int z = 0; z <= BAY_EDGE; z++) {
                helper.setBlock(new BlockPos(x, 0, z), z <= COBBLE_ROWS_END ? Blocks.COBBLESTONE : Blocks.WATER);
            }
        }
        helper.setBlock(SAND, Blocks.SAND);
        helper.setBlock(CANE, Blocks.SUGAR_CANE);
        helper.setBlock(PRISM, GooBlocks.PRISM.get().defaultBlockState().setValue(PrismBlock.FACING, Direction.UP));
        AbilityDefinition verdant = AbilityRegistry.of(helper.getLevel()).getAbility(VERDANT);
        helper.assertTrue(verdant != null, ABILITY_REQUIRED);
        helper.runAfterDelay(1, () -> AbilityImpact.land(helper.getLevel(), helper.absolutePos(PRISM), GooTypes.LEAF,
                Direction.UP, verdant));
        helper.succeedWhen(() -> {
            boolean mossy = anyAt(helper, 0, Blocks.MOSSY_COBBLESTONE.defaultBlockState());
            boolean lily = anyAt(helper, 1, Blocks.LILY_PAD.defaultBlockState());
            BlockState cane = helper.getBlockState(CANE);
            boolean nudged = cane.is(Blocks.SUGAR_CANE)
                    && (cane.getValue(SugarCaneBlock.AGE) > 0 || helper.getBlockState(CANE.above()).is(Blocks.SUGAR_CANE));
            helper.assertTrue(mossy && lily && nudged, String.format(SHOULD_GREEN, mossy, lily, cane));
        });
    }

    private static boolean anyAt(GameTestHelper helper, int y, BlockState wanted) {
        for (int x = 0; x <= BAY_EDGE; x++) {
            for (int z = 0; z <= BAY_EDGE; z++) {
                if (helper.getBlockState(new BlockPos(x, y, z)).is(wanted.getBlock())) {
                    return true;
                }
            }
        }
        return false;
    }
}
