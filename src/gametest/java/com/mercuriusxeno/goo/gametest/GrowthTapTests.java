package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.block.tap.TapDripScheduler;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.SugarCaneBlock;

/**
 * Gametests for Growth's tap: drips landing beside plants tick them only once
 * the JSON's drip count has landed. Sugar cane ages one step on every random
 * tick, so it shows the count exactly: three drips leave it as planted and
 * the fourth ages it once. Wheat on the same pulses grows.
 * growth-drip-pulses-plants-below
 */
public final class GrowthTapTests {

    /** leaf_growth_tap.json's drip count. */
    private static final int DRIPS = 4;
    /** Pulses of drips wheat takes, enough that a one-in-several crop roll comes up many times over. */
    private static final int WHEAT_PULSES = 60;
    /** The sand the drips land on, resting on the bay floor, the cane standing on it. */
    private static final BlockPos SAND = new BlockPos(2, 1, 2);
    private static final BlockPos CANE = SAND.above();
    /** Moist farmland a block east of the sand, inside the pulse's reach, the wheat standing on it. */
    private static final BlockPos FARMLAND = SAND.east();
    private static final BlockPos WHEAT = FARMLAND.above();
    private static final String COUNT_SHORT = "Drips short of the count should leave the cane at age 0, stands at %d";
    private static final String COUNT_MET = "The count's last drip should age the cane once, stands at %d";
    private static final String WHEAT_GROWS = "Wheat under %d pulses should grow, stands at age %d";

    private GrowthTapTests() {
    }

    /**
     * Three drips beside sugar cane and wheat leave both as planted; the
     * fourth ages the cane by exactly one; many pulses more grow the wheat.
     *
     * @param helper the gametest helper
     */
    public static void growthTapCountsDrips(GameTestHelper helper) {
        helper.setBlock(SAND.below(), Blocks.STONE);
        helper.setBlock(SAND, Blocks.SAND);
        helper.setBlock(SAND.west(), Blocks.WATER);
        helper.setBlock(CANE, Blocks.SUGAR_CANE);
        helper.setBlock(FARMLAND, Blocks.FARMLAND.defaultBlockState()
                .setValue(FarmlandBlock.MOISTURE, FarmlandBlock.MAX_MOISTURE));
        helper.setBlock(WHEAT, Blocks.WHEAT);
        helper.runAfterDelay(1, () -> {
            drip(helper, DRIPS - 1);
            int shortAge = helper.getBlockState(CANE).getValue(SugarCaneBlock.AGE);
            helper.assertTrue(shortAge == 0 && helper.getBlockState(WHEAT).getValue(CropBlock.AGE) == 0,
                    String.format(COUNT_SHORT, shortAge));
            drip(helper, 1);
            int metAge = helper.getBlockState(CANE).getValue(SugarCaneBlock.AGE);
            helper.assertTrue(metAge == 1, String.format(COUNT_MET, metAge));
            drip(helper, DRIPS * WHEAT_PULSES);
            int wheatAge = helper.getBlockState(WHEAT).getValue(CropBlock.AGE);
            helper.assertTrue(wheatAge > 0, String.format(WHEAT_GROWS, WHEAT_PULSES, wheatAge));
            helper.succeed();
        });
    }

    /**
     * Lands leaf drips on the sand's top face, each running leaf's tap ability.
     *
     * @param helper the gametest helper
     * @param drips  how many drips land
     */
    private static void drip(GameTestHelper helper, int drips) {
        BlockPos landing = helper.absolutePos(SAND);
        AbilityRegistry abilities = AbilityRegistry.of(helper.getLevel());
        for (int dripped = 0; dripped < drips; dripped++) {
            TapDripScheduler.runTapAbility(new TapDripScheduler.PendingDrip(helper.getLevel(), landing.above(),
                    landing, Direction.UP, GooTypes.LEAF, 1, 0), abilities);
        }
    }
}
