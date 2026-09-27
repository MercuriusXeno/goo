package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.block.canister.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.canister.CanisterSlotLayout;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Gametests for where a canister item places a new canister block: on a top face covering the
 * central 12x12 pixels, and nowhere else (decision canister-support-is-rigid-top-face).
 */
public final class CanisterPlacementTests {

    private static final BlockPos SUPPORT_POS = new BlockPos(1, 1, 1);
    private static final BlockPos CANISTER_POS = SUPPORT_POS.above();
    private static final int AIMED_SLOT = 0;
    private static final double PIXELS_PER_BLOCK = 16.0;

    private CanisterPlacementTests() {}

    /**
     * A canister used on an empty vat's top at slot 0's pixel center places a canister block
     * above the vat holding that canister in slot 0.
     *
     * @param helper the gametest helper
     */
    public static void vatTopTakesCanister(GameTestHelper helper) {
        helper.setBlock(SUPPORT_POS, GooBlocks.VAT.get());
        helper.useBlock(SUPPORT_POS, playerHoldingCanister(helper), aimedSlotTopHit(helper));
        assertCanisterInAimedSlot(helper);
        helper.succeed();
    }

    /**
     * A canister used on a stone top places a canister block above it.
     *
     * @param helper the gametest helper
     */
    public static void stoneTopTakesCanister(GameTestHelper helper) {
        helper.setBlock(SUPPORT_POS, Blocks.STONE);
        helper.useBlock(SUPPORT_POS, playerHoldingCanister(helper), aimedSlotTopHit(helper));
        assertCanisterInAimedSlot(helper);
        helper.succeed();
    }

    /**
     * A canister used on a crucible's top places nothing: the hollow rim leaves the central
     * 12x12 uncovered.
     *
     * @param helper the gametest helper
     */
    public static void crucibleTopRefusesCanister(GameTestHelper helper) {
        assertItemUseOnTopPlacesNothing(helper, GooBlocks.CRUCIBLE.get());
    }

    /**
     * A canister used on a standing sign's top places nothing: the sign's 8x8 post leaves the
     * central 12x12 uncovered.
     *
     * @param helper the gametest helper
     */
    public static void signTopRefusesCanister(GameTestHelper helper) {
        assertItemUseOnTopPlacesNothing(helper, Blocks.OAK_SIGN);
    }

    /**
     * Runs the canister item's own use on the block's top, the path a sneaking player's click
     * takes, so the block's own click handler does not answer first.
     *
     * @param helper  the gametest helper
     * @param support the block the canister is used on
     */
    private static void assertItemUseOnTopPlacesNothing(GameTestHelper helper, Block support) {
        helper.setBlock(SUPPORT_POS, support);
        Player player = playerHoldingCanister(helper);
        player.setShiftKeyDown(true);
        player.getItemInHand(InteractionHand.MAIN_HAND)
            .useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, aimedSlotTopHit(helper)));
        helper.assertBlockNotPresent(GooBlocks.CANISTER.get(), CANISTER_POS);
        helper.assertTrue(helper.getBlockState(CANISTER_POS).isAir(), "Nothing should stand above the refused top");
        helper.succeed();
    }

    private static Player playerHoldingCanister(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.CANISTER.get()));
        return player;
    }

    /**
     * A hit on the support block's top face at the aimed slot's pixel center.
     *
     * @param helper the gametest helper
     * @return the hit result
     */
    private static BlockHitResult aimedSlotTopHit(GameTestHelper helper) {
        BlockPos abs = helper.absolutePos(SUPPORT_POS);
        float[] center = CanisterSlotLayout.SLOT_CENTERS[AIMED_SLOT];
        Vec3 location = new Vec3(abs.getX() + center[0] / PIXELS_PER_BLOCK, abs.getY() + 1.0,
            abs.getZ() + center[1] / PIXELS_PER_BLOCK);
        return new BlockHitResult(location, Direction.UP, abs, false);
    }

    private static void assertCanisterInAimedSlot(GameTestHelper helper) {
        helper.assertBlockPresent(GooBlocks.CANISTER.get(), CANISTER_POS);
        CanisterBlockEntity canister = helper.getBlockEntity(CANISTER_POS, CanisterBlockEntity.class);
        helper.assertFalse(canister.containerState().getCanister(AIMED_SLOT).isEmpty(),
            "The placed canister should fill the aimed slot");
    }
}
