package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.block.tap.TapDripScheduler;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PointedDripstoneBlock;

/**
 * Gametests for rock petrify's tap: drips landing on gravel leave it gravel
 * until the JSON's drip count, then step it to cobblestone; drips landing on
 * a dripstone block grow a pointed dripstone tip under it
 * (decision petrify-drip-calcifies-and-grows-dripstone).
 */
public final class PetrifyTapTests {

    /** rock_petrify_tap.json's drip count. */
    private static final int DRIPS = 8;
    /** A block resting on the bay floor, where the gravel lies. */
    private static final BlockPos ON_THE_FLOOR = new BlockPos(2, 0, 2);
    /** A dripstone block two above the floor, an open cell under it. */
    private static final BlockPos HANGING = new BlockPos(2, 2, 2);
    private static final String SHOULD_GROW_A_TIP = "A pointed dripstone tip should hang under the dripstone, stands %s";

    private PetrifyTapTests() {
    }

    /**
     * Seven drips on gravel leave it gravel; the eighth steps it to cobblestone.
     *
     * @param helper the gametest helper
     */
    public static void petrifyTapCalcifies(GameTestHelper helper) {
        helper.setBlock(ON_THE_FLOOR, Blocks.GRAVEL);
        helper.runAfterDelay(1, () -> {
            drip(helper, ON_THE_FLOOR, DRIPS - 1);
            helper.assertBlockPresent(Blocks.GRAVEL, ON_THE_FLOOR);
            drip(helper, ON_THE_FLOOR, 1);
            helper.assertBlockPresent(Blocks.COBBLESTONE, ON_THE_FLOOR);
            helper.succeed();
        });
    }

    /**
     * Eight drips on a dripstone block grow a pointed dripstone tip, pointing
     * down, in the open cell under it.
     *
     * @param helper the gametest helper
     */
    public static void petrifyTapGrowsDripstone(GameTestHelper helper) {
        helper.setBlock(HANGING, Blocks.DRIPSTONE_BLOCK);
        helper.setBlock(HANGING.below(), Blocks.AIR);
        helper.runAfterDelay(1, () -> {
            drip(helper, HANGING, DRIPS);
            var grown = helper.getBlockState(HANGING.below());
            helper.assertTrue(grown.is(Blocks.POINTED_DRIPSTONE)
                            && grown.getValue(PointedDripstoneBlock.TIP_DIRECTION) == Direction.DOWN,
                    String.format(SHOULD_GROW_A_TIP, grown));
            helper.succeed();
        });
    }

    /**
     * Lands rock drips on a block's top face, each running rock's tap ability.
     *
     * @param helper  the gametest helper
     * @param landing the block the drips land on, relative
     * @param drips   how many drips land
     */
    private static void drip(GameTestHelper helper, BlockPos landing, int drips) {
        BlockPos landingAbs = helper.absolutePos(landing);
        AbilityRegistry abilities = AbilityRegistry.of(helper.getLevel());
        for (int dripped = 0; dripped < drips; dripped++) {
            TapDripScheduler.runTapAbility(new TapDripScheduler.PendingDrip(helper.getLevel(), landingAbs.above(),
                    landingAbs, Direction.UP, GooTypes.ROCK, 1, 0), abilities);
        }
    }
}
