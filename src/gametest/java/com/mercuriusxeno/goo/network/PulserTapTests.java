package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.block.tap.TapBlock;
import com.mercuriusxeno.goo.block.tap.TapBlockEntity;
import com.mercuriusxeno.goo.block.tap.TapDripGrade;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;

/**
 * Gametest for Pulser's tap: a pulse tap over a lever flips it once for
 * each drip that falls through it.
 * pulser-drip-toggles-the-block-below
 */
public final class PulserTapTests {

    private static final BlockPos TAP_POS = new BlockPos(1, 3, 1);
    /** The lever below the tap, standing on stone; a drip falls through it onto the stone. */
    private static final BlockPos LEVER_POS = new BlockPos(1, 1, 1);
    /** Three drips of 1 mB each, and the tap runs dry. */
    private static final int DRIPS = 3;
    /** Past the three drips at one per 4 ticks and their falls. */
    private static final int WATCH_TICKS = 60;
    private static final String TAP_ABILITY_REQUIRED = "Ability registry must hold a pulse tap ability";
    private static final String FLIPS_PER_DRIP = "The lever should flip once per drip, %d drips, flipped %d times";

    private PulserTapTests() {
    }

    /**
     * A pulse tap holding three drips' goo over a lever: the lever flips
     * three times.
     *
     * @param helper the gametest helper
     */
    public static void pulserTapFlipsPerDrip(GameTestHelper helper) {
        helper.assertTrue(AbilityRegistry.of(helper.getLevel()).tapAbilityFor(GooTypes.PULSE) != null,
                TAP_ABILITY_REQUIRED);
        helper.setBlock(LEVER_POS.below(), Blocks.STONE);
        helper.setBlock(LEVER_POS, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR));
        helper.setBlock(LEVER_POS.above(), Blocks.AIR);
        helper.setBlock(TAP_POS, GooBlocks.TAP.get().defaultBlockState().setValue(TapBlock.OPEN, true));
        TapBlockEntity tap = helper.getBlockEntity(TAP_POS, TapBlockEntity.class);
        tap.insertCanister(new ItemStack(GooItems.CANISTER.get()));
        tap.insertGoo(GooTypes.PULSE, DRIPS);
        tap.setDripGrade(TapDripGrade.ONE_PER_4_TICKS);
        int[] flips = {0};
        boolean[] last = {powered(helper)};
        for (int tick = 1; tick <= WATCH_TICKS; tick++) {
            helper.runAfterDelay(tick, () -> {
                boolean now = powered(helper);
                if (now != last[0]) {
                    flips[0]++;
                    last[0] = now;
                }
            });
        }
        helper.runAfterDelay(WATCH_TICKS + 1, () -> {
            helper.assertTrue(flips[0] == DRIPS, String.format(FLIPS_PER_DRIP, DRIPS, flips[0]));
            helper.succeed();
        });
    }

    private static boolean powered(GameTestHelper helper) {
        return helper.getBlockState(LEVER_POS).getValue(LeverBlock.POWERED);
    }
}
