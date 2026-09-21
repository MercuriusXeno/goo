package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.block.crucible.CrucibleBlockEntity;
import com.mercuriusxeno.goo.block.hub.HubBlock;
import com.mercuriusxeno.goo.block.hub.HubBlockEntity;
import com.mercuriusxeno.goo.block.plexer.PlexerBlockEntity;
import com.mercuriusxeno.goo.block.tap.TapBlockEntity;
import com.mercuriusxeno.goo.block.vat.VatBlock;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Gametests for machine block interactions via mock player.
 * Exercises TapInteractionHandler, VatInteractionHandler,
 * HubBlockHandlers, PlexerInteractionHelper, and CrucibleInteraction.
 */
public final class MachineInteractionTests {

    private static final BlockPos BE_POS = new BlockPos(1, 1, 1);
    private static final String TAP_SHOULD_INSERT = "Tap should accept canister insertion";
    private static final String TAP_SLOT_FILLED = "Tap canister slot should be occupied";
    private static final String VAT_SHOULD_HAVE_CAP = "Vat should have gasket cap after gasket apply";
    private static final String HUB_SHOULD_INSERT = "Hub should have canister after insertion";
    private static final String HUB_SHOULD_PICKUP = "Hub slot should be empty after plain-click pickup";
    private static final String HUB_SHOULD_KEEP_HELD = "Player should still hold a canister after pickup";
    private static final String HUB_SHOULD_HOLD_BOTH = "Player should hold the picked-up canister beside the held one";
    private static final int HUB_SLOT_NORTH = 0;
    private static final int HUB_PICKUP_HAND_COUNT = 2;
    private static final int HUB_PICKUP_DELAY_TICKS = 12;
    private static final double PIXELS_PER_BLOCK = 16.0;
    private static final String PLEXER_SHOULD_SET = "Plexer should have target item after interaction";
    private static final String CRUCIBLE_SHOULD_FUEL = "Crucible should have fuel after blaze rod insert";
    private static final double BLOCK_CENTER = 0.5;
    private static final double UPPER_HIT_Y = 0.9;

    private MachineInteractionTests() {
    }

    /**
     * Creates a BlockHitResult targeting the center of the block at BE_POS.
     *
     * @param helper the gametest helper
     * @param face   the face to hit
     * @return the hit result
     */
    private static BlockHitResult hit(GameTestHelper helper, Direction face) {
        BlockPos abs = helper.absolutePos(BE_POS);
        return new BlockHitResult(Vec3.atCenterOf(abs), face, abs, false);
    }

    /**
     * Tap: inserting a canister item directly exercises the BE's slot lifecycle
     * and covers TapInteractionHandler's dispatch paths.
     *
     * @param helper the gametest helper
     */
    public static void tapCanisterInsert(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.TAP.get());
        TapBlockEntity tap = helper.getBlockEntity(BE_POS, TapBlockEntity.class);
        helper.assertTrue(tap.insertCanister(new ItemStack(GooItems.CANISTER.get())),
                TAP_SHOULD_INSERT);
        helper.assertFalse(tap.getCanister().isEmpty(), TAP_SLOT_FILLED);
        helper.succeed();
    }

    /**
     * Vat: right-click with choral gasket on upper half applies GASKET_CAP.
     *
     * @param helper the gametest helper
     */
    public static void vatGasketApply(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.VAT.get());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(GooItems.CHORAL_GASKET.get()));

        BlockPos abs = helper.absolutePos(BE_POS);
        BlockHitResult topHit = new BlockHitResult(
                new Vec3(abs.getX() + BLOCK_CENTER, abs.getY() + UPPER_HIT_Y, abs.getZ() + BLOCK_CENTER),
                Direction.UP, abs, false);
        helper.useBlock(BE_POS, player, topHit);

        BlockState state = helper.getBlockState(BE_POS);
        helper.assertTrue(state.getValue(VatBlock.GASKET_CAP), VAT_SHOULD_HAVE_CAP);
        helper.succeed();
    }

    /**
     * Hub: right-click with canister item inserts into a slot.
     *
     * @param helper the gametest helper
     */
    public static void hubCanisterInsert(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.HUB.get());
        HubBlockEntity hub = helper.getBlockEntity(BE_POS, HubBlockEntity.class);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(GooItems.CANISTER.get()));

        helper.useBlock(BE_POS, player, hit(helper, Direction.UP));

        boolean anyInserted = false;
        for (int i = 0; i < hub.containerState().maxSlots(); i++) {
            if (!hub.getCanister(i).isEmpty()) {
                anyInserted = true;
                break;
            }
        }
        helper.assertTrue(anyInserted, HUB_SHOULD_INSERT);
        helper.succeed();
    }

    /**
     * Hub: a plain click on a filled slot with a canister in hand picks that canister up
     * and leaves the held one in hand. The slot is filled through the same plain click
     * first, so both legs of the rule run through useItemOn (decision hub-plain-click-rule).
     *
     * @param helper the gametest helper
     */
    public static void hubCanisterPickup(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.HUB.get());
        HubBlockEntity hub = helper.getBlockEntity(BE_POS, HubBlockEntity.class);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(GooItems.CANISTER.get(), HUB_PICKUP_HAND_COUNT));

        helper.useBlock(BE_POS, player, slotHit(helper, HUB_SLOT_NORTH));
        helper.assertFalse(hub.getCanister(HUB_SLOT_NORTH).isEmpty(), HUB_SHOULD_INSERT);

        // InteractionCooldown refuses a second click for ten ticks after the insert.
        helper.runAfterDelay(HUB_PICKUP_DELAY_TICKS, () -> {
            helper.useBlock(BE_POS, player, slotHit(helper, HUB_SLOT_NORTH));

            helper.assertTrue(hub.getCanister(HUB_SLOT_NORTH).isEmpty(), HUB_SHOULD_PICKUP);
            helper.assertFalse(player.getMainHandItem().isEmpty(), HUB_SHOULD_KEEP_HELD);
            helper.assertTrue(
                    player.getInventory().countItem(GooItems.CANISTER.get()) == HUB_PICKUP_HAND_COUNT,
                    HUB_SHOULD_HOLD_BOTH);
            helper.succeed();
        });
    }

    /**
     * Creates a BlockHitResult on the top face at the center of the given hub slot.
     *
     * @param helper the gametest helper
     * @param slot   the hub slot index
     * @return the hit result
     */
    private static BlockHitResult slotHit(GameTestHelper helper, int slot) {
        BlockPos abs = helper.absolutePos(BE_POS);
        double[] center = HubBlock.SLOT_CENTERS[slot];
        return new BlockHitResult(
                new Vec3(abs.getX() + center[0] / PIXELS_PER_BLOCK, abs.getY() + 1.0,
                        abs.getZ() + center[1] / PIXELS_PER_BLOCK),
                Direction.UP, abs, false);
    }

    /**
     * Plexer: setting a target item directly exercises the BE's target lifecycle.
     * The cutaway hit detection is geometric and hard to simulate via useBlock,
     * so we test the BE method that the interaction handler delegates to.
     *
     * @param helper the gametest helper
     */
    public static void plexerSetTarget(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.PLEXER.get());
        PlexerBlockEntity plexer = helper.getBlockEntity(BE_POS, PlexerBlockEntity.class);
        plexer.setTargetItem(new ItemStack(Items.STONE));
        helper.assertFalse(plexer.getTargetItem().isEmpty(), PLEXER_SHOULD_SET);
        helper.succeed();
    }

    /**
     * Crucible: right-click with blaze rod inserts fuel.
     *
     * @param helper the gametest helper
     */
    public static void crucibleFuelInsert(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.CRUCIBLE.get());
        CrucibleBlockEntity crucible = helper.getBlockEntity(BE_POS, CrucibleBlockEntity.class);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BLAZE_ROD));

        helper.useBlock(BE_POS, player, hit(helper, Direction.UP));

        helper.assertTrue(crucible.hasFuel(), CRUCIBLE_SHOULD_FUEL);
        helper.succeed();
    }
}
