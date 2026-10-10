package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;

/**
 * Gametest for Signal: a long hold along a row of two levers, one near and
 * one far, flips each lever exactly once.
 * signal-wave-toggles-each-device-once
 */
public final class SignalTests {

    private static final Identifier SIGNAL = Identifier.parse("goo:pulse_signal");
    private static final BlockPos STAND_POS = new BlockPos(1, 1, 3);
    /** Two and four blocks ahead of the player, a lever on a stone post at eye height. */
    private static final BlockPos NEAR_LEVER = STAND_POS.east(2).above();
    private static final BlockPos FAR_LEVER = STAND_POS.east(4).above();
    private static final float FACING_EAST = -90f;
    private static final float LEVEL_LOOK = 0f;
    private static final int HELD_GOO = 3;
    /** Far past the sixteen ticks pulse_signal.json's front takes to reach its range. */
    private static final int HOLD_TICKS = 60;
    private static final String ABILITY_REQUIRED = "Ability registry must hold pulse_signal";
    private static final String FLIPS_ONCE = "The %s lever should flip exactly once in the hold, flipped %d times";

    private SignalTests() {
    }

    /**
     * A mock player holds Signal along two levers for sixty ticks: each lever
     * flips exactly once in the hold.
     *
     * @param helper the gametest helper
     */
    public static void signalTogglesEachOnce(GameTestHelper helper) {
        AbilityDefinition signal = AbilityRegistry.of(helper.getLevel()).getAbility(SIGNAL);
        helper.assertTrue(signal != null, ABILITY_REQUIRED);
        postLever(helper, NEAR_LEVER);
        postLever(helper, FAR_LEVER);
        ServerPlayer player = signaller(helper);
        KnownRecipes.teachRequires(player, signal);
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(GooTypes.PULSE), SIGNAL.toString(),
                player.getEyePosition(), player.getEyePosition());
        FlipCount near = new FlipCount(powered(helper, NEAR_LEVER));
        FlipCount far = new FlipCount(powered(helper, FAR_LEVER));
        for (int held = 1; held <= HOLD_TICKS; held++) {
            helper.runAfterDelay(held, () -> {
                GooStreamHandler.streamTick(player, tick);
                near.see(powered(helper, NEAR_LEVER));
                far.see(powered(helper, FAR_LEVER));
            });
        }
        helper.runAfterDelay(HOLD_TICKS + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(near.flips == 1, String.format(FLIPS_ONCE, "near", near.flips));
            helper.assertTrue(far.flips == 1, String.format(FLIPS_ONCE, "far", far.flips));
            helper.succeed();
        });
    }

    private static void postLever(GameTestHelper helper, BlockPos lever) {
        helper.setBlock(lever.below(), Blocks.STONE);
        helper.setBlock(lever, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR));
    }

    private static boolean powered(GameTestHelper helper, BlockPos lever) {
        return helper.getBlockState(lever).getValue(LeverBlock.POWERED);
    }

    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer signaller(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STAND_POS));
        player.setPos(stand.x, stand.y, stand.z);
        player.setYRot(FACING_EAST);
        player.setXRot(LEVEL_LOOK);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(GooTypes.PULSE, HELD_GOO * GooStacks.THOUSAND));
        return player;
    }

    /** Counts a lever's flips tick by tick. */
    private static final class FlipCount {

        private boolean last;
        private int flips;

        FlipCount(boolean start) {
            last = start;
        }

        void see(boolean now) {
            if (now != last) {
                flips++;
                last = now;
            }
        }
    }
}
