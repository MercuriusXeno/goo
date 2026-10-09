package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.block.crucible.CrucibleBasin;
import com.mercuriusxeno.goo.block.crucible.CrucibleBlockEntity;
import com.mercuriusxeno.goo.block.tap.TapDripScheduler;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Gametests for aeon Tick's tap: aeon drips landing on a melting crucible
 * tick it faster only once aeon_tick_tap.json's drip count has landed.
 * tick-drip-splashes-a-small-tick-effect
 */
public final class TickTapTests {

    private static final BlockPos CRUCIBLE_POS = CrucibleSpawns.CRUCIBLE_POS;
    /** Heat for an hour of melting, so the crucible never runs cold. */
    private static final int HEAT_TICKS = 72_000;
    /** Ticks a still item dropped at the basin center takes to land, rest and be consumed. */
    private static final int ABSORB_TICKS = 10;
    private static final int STACK = 64;
    private static final double JUST_ABOVE_FLOOR = 0.05;
    private static final double BASIN_CENTER = 0.5;
    /** aeon_tick_tap.json's drip count. */
    private static final int DRIPS = 4;
    private static final String SHOULD_MELT = "The crucible should hold the stack melting, pool %d";
    private static final String SHOULD_WAIT = "Drips short of the count should leave the melt where it stood, %d then %d";
    private static final String SHOULD_TICK = "The drip that fills the count should hasten the melt, %d then %d";

    private TickTapTests() {
    }

    /**
     * Within one game tick, so the crucible's own ticking stands still, three
     * aeon drips leave its melt where it stood and the fourth melts more of it.
     *
     * @param helper the gametest helper
     */
    public static void tickTapCountsDrips(GameTestHelper helper) {
        helper.setBlock(CRUCIBLE_POS, GooBlocks.CRUCIBLE.get());
        CrucibleBlockEntity crucible = helper.getBlockEntity(CRUCIBLE_POS, CrucibleBlockEntity.class);
        crucible.addHeat(HEAT_TICKS);
        CrucibleSpawns.spawnAt(helper, new ItemStack(Items.COBBLESTONE, STACK), new Vec3(
                CRUCIBLE_POS.getX() + BASIN_CENTER, CRUCIBLE_POS.getY() + CrucibleBasin.FLOOR_Y + JUST_ABOVE_FLOOR,
                CRUCIBLE_POS.getZ() + BASIN_CENTER));
        helper.runAfterDelay(ABSORB_TICKS, () -> {
            long before = crucible.getPoolVolume();
            helper.assertTrue(before > 0, String.format(SHOULD_MELT, before));
            drip(helper, DRIPS - 1);
            long shortOfCount = crucible.getPoolVolume();
            helper.assertTrue(shortOfCount == before, String.format(SHOULD_WAIT, before, shortOfCount));
            drip(helper, 1);
            long counted = crucible.getPoolVolume();
            helper.assertTrue(counted < shortOfCount, String.format(SHOULD_TICK, shortOfCount, counted));
            helper.succeed();
        });
    }

    /**
     * Lands aeon drips on the crucible's top face, each running aeon's tap ability.
     *
     * @param helper the gametest helper
     * @param drips  how many drips land
     */
    private static void drip(GameTestHelper helper, int drips) {
        BlockPos landing = helper.absolutePos(CRUCIBLE_POS);
        AbilityRegistry abilities = AbilityRegistry.of(helper.getLevel());
        for (int dripped = 0; dripped < drips; dripped++) {
            TapDripScheduler.runTapAbility(new TapDripScheduler.PendingDrip(helper.getLevel(), landing.above(2),
                    landing, Direction.UP, GooTypes.AEON, 1, 0), abilities);
        }
    }
}
