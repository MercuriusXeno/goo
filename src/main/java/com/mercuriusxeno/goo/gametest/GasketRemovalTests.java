package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.block.canister.CanisterBlock;
import com.mercuriusxeno.goo.block.canister.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.crucible.CrucibleBlock;
import com.mercuriusxeno.goo.block.crucible.CrucibleBlockEntity;
import com.mercuriusxeno.goo.block.gasket.IGasketHolder;
import com.mercuriusxeno.goo.block.hub.HubBlock;
import com.mercuriusxeno.goo.block.hub.HubBlockEntity;
import com.mercuriusxeno.goo.block.reactor.ReactorBlock;
import com.mercuriusxeno.goo.block.reactor.ReactorBlockEntity;
import com.mercuriusxeno.goo.block.tap.TapBlock;
import com.mercuriusxeno.goo.block.tap.TapBlockEntity;
import com.mercuriusxeno.goo.block.vat.VatBlock;
import com.mercuriusxeno.goo.block.vat.VatBlockEntity;
import com.mercuriusxeno.goo.data.GasketRegistry;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;
import static com.mercuriusxeno.goo.GooConstants.NO_SLOT;

/**
 * Gametests for the one sneak empty-hand gasket removal route every machine
 * shares (decision sneak-empty-hand-pops-hit-gasket): each gasket is installed
 * through the gasket item's own click, then a sneak empty-hand click on its
 * region pops it into the player's inventory and a click on a bare region runs
 * the machine's standing empty-hand behavior.
 */
public final class GasketRemovalTests {

    private static final BlockPos BE_POS = new BlockPos(1, 1, 1);
    private static final double HALF = 0.5;
    /** Above every machine's gasket midpoint: resolves the top (receiver) face. */
    private static final double UPPER_Y = 0.75;
    /** Below every machine's gasket midpoint: resolves the bottom (transmitter) face. */
    private static final double LOWER_Y = 0.25;
    /** Reactor output hollow heights, either side of the output canister's midpoint. */
    private static final double REACTOR_UPPER_Y = 0.65;
    private static final double PIXEL = 1.0 / 16.0;
    /** A point on the south-facing tap body, clear of the valve and the canister slot. */
    private static final double TAP_BODY_Y = 2.0 / 16.0;
    private static final double TAP_BODY_Z = 3.0 / 16.0;
    private static final int HUB_SLOT = 0;
    /** The hub test pops the intake gasket and a slot gasket. */
    private static final int HUB_GASKETS = 2;

    private static final String GASKET_IN_HAND = "The popped gasket should reach the player's inventory";
    private static final String GASKET_ID_CLEARED = "The holder should no longer register the popped gasket";
    private static final String LOCATION_CLEARED = "The registry should no longer locate the popped gasket";
    private static final String OTHER_GASKET_KEPT = "A gasket the click did not hit should stay installed";
    private static final String GASKET_INSTALLED = "The gasket item's click should install a gasket";
    private static final String CANISTER_KEPT = "Popping a gasket should leave the canister in place";
    private static final String CANISTER_HANDED_BACK = "A sneak click on a bare region should hand back the canister";
    private static final String HEAT_KEPT = "Popping the crucible gasket should leave the heat in place";
    private static final String GASKETLESS_SNEAK_PASSES =
        "A sneak click on a gasketless crucible should pass, leaving its heat";
    private static final int CRUCIBLE_HEAT_TICKS = 20;
    private static final String NO_EXTRA_GASKET = "A bare-region click should pop no gasket";
    private static final String STATE_UNCHANGED = "A bare-region click should leave the machine's state unchanged";

    private GasketRemovalTests() {
    }

    // --- Canister block (criterion: slot gasket pops, canister stays) ---

    /**
     * Canister: sneak empty-hand on a slot's gasketed top half pops that gasket
     * into the player's inventory and the slot keeps its canister.
     *
     * @param helper the gametest helper
     */
    public static void canisterSlotGasketPopsCanisterStays(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.CANISTER.get());
        CanisterBlockEntity canister = helper.getBlockEntity(BE_POS, CanisterBlockEntity.class);
        canister.insertCanister(CanisterBlock.CENTER_SLOT, new ItemStack(GooItems.CANISTER.get()), false);
        Player player = playerOnLastHotbarSlot(helper);
        BlockHitResult upper = hitAt(helper, HALF, UPPER_Y, HALF, Direction.UP);
        UUID gasketId = installGasket(helper, player, upper, canister, GasketRole.RECEIVER, CanisterBlock.CENTER_SLOT);

        sneakEmptyHanded(player);
        helper.useBlock(BE_POS, player, upper);

        assertPopped(helper, player, canister, GasketRole.RECEIVER, CanisterBlock.CENTER_SLOT, gasketId);
        helper.assertFalse(canister.getCanister(CanisterBlock.CENTER_SLOT).isEmpty(), CANISTER_KEPT);
        helper.succeed();
    }

    // --- Hub ---

    /**
     * Hub: sneak empty-hand pops the intake gasket off any slot and a slot's
     * top gasket on that slot, each alone; the same click on the slot once bare
     * hands back its canister.
     *
     * @param helper the gametest helper
     */
    public static void hubPopsHitGasketThenHandsBackCanister(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.HUB.get());
        HubBlockEntity hub = helper.getBlockEntity(BE_POS, HubBlockEntity.class);
        hub.insertCanister(HUB_SLOT, new ItemStack(GooItems.CANISTER.get()));
        Player player = playerOnLastHotbarSlot(helper);
        BlockHitResult intake = hitAt(helper, HALF, 1.0, HALF, Direction.UP);
        double[] slotCenter = HubBlock.SLOT_CENTERS[HUB_SLOT];
        BlockHitResult slotUpper = hitAt(helper, slotCenter[0] * PIXEL, UPPER_Y, slotCenter[1] * PIXEL, Direction.NORTH);
        UUID intakeId = installGasket(helper, player, intake, hub, GasketRole.RECEIVER, NO_SLOT);
        UUID slotId = installGasket(helper, player, slotUpper, hub, GasketRole.RECEIVER, HUB_SLOT);

        sneakEmptyHanded(player);
        helper.useBlock(BE_POS, player, intake);
        assertPopped(helper, player, hub, GasketRole.RECEIVER, NO_SLOT, intakeId);
        helper.assertFalse(helper.getBlockState(BE_POS).getValue(HubBlock.HAS_GASKET), GASKET_ID_CLEARED);
        helper.assertTrue(slotId.equals(hub.getGasketId(GasketRole.RECEIVER, HUB_SLOT)), OTHER_GASKET_KEPT);

        helper.useBlock(BE_POS, player, slotUpper);
        helper.assertTrue(gasketsHeld(player) == HUB_GASKETS, GASKET_IN_HAND);
        helper.assertTrue(hub.getGasketId(GasketRole.RECEIVER, HUB_SLOT) == null, GASKET_ID_CLEARED);
        helper.assertFalse(hub.getCanister(HUB_SLOT).isEmpty(), CANISTER_KEPT);

        helper.useBlock(BE_POS, player, slotUpper);
        helper.assertTrue(hub.getCanister(HUB_SLOT).isEmpty(), CANISTER_HANDED_BACK);
        helper.assertTrue(player.getInventory().countItem(GooItems.CANISTER.get()) == 1, CANISTER_HANDED_BACK);
        helper.assertTrue(gasketsHeld(player) == HUB_GASKETS, NO_EXTRA_GASKET);
        helper.succeed();
    }

    // --- Tap ---

    /**
     * Tap: sneak empty-hand pops the tap's gasket while a canister is seated and
     * leaves the canister; the same click once the tap is bare hands back the canister.
     *
     * @param helper the gametest helper
     */
    public static void tapPopsGasketThenHandsBackCanister(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.TAP.get());
        TapBlockEntity tap = helper.getBlockEntity(BE_POS, TapBlockEntity.class);
        tap.insertCanister(new ItemStack(GooItems.CANISTER.get()));
        Player player = playerOnLastHotbarSlot(helper);
        BlockHitResult body = hitAt(helper, HALF, TAP_BODY_Y, TAP_BODY_Z, Direction.UP);
        UUID gasketId = installGasket(helper, player, body, tap, GasketRole.RECEIVER, NO_SLOT);

        sneakEmptyHanded(player);
        helper.useBlock(BE_POS, player, body);
        assertPopped(helper, player, tap, GasketRole.RECEIVER, NO_SLOT, gasketId);
        helper.assertFalse(helper.getBlockState(BE_POS).getValue(TapBlock.HAS_GASKET), GASKET_ID_CLEARED);
        helper.assertFalse(tap.getCanister().isEmpty(), CANISTER_KEPT);

        helper.useBlock(BE_POS, player, body);
        helper.assertTrue(tap.getCanister().isEmpty(), CANISTER_HANDED_BACK);
        helper.assertTrue(gasketsHeld(player) == 1, NO_EXTRA_GASKET);
        helper.succeed();
    }

    // --- Vat ---

    /**
     * Vat: sneak empty-hand on the lower half pops the base gasket and keeps the
     * cap; the same click once the base is bare leaves the vat unchanged.
     *
     * @param helper the gametest helper
     */
    public static void vatPopsHitFaceThenLeavesStateUnchanged(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.VAT.get());
        VatBlockEntity vat = helper.getBlockEntity(BE_POS, VatBlockEntity.class);
        Player player = playerOnLastHotbarSlot(helper);
        BlockHitResult upper = hitAt(helper, HALF, UPPER_Y, 0.0, Direction.NORTH);
        BlockHitResult lower = hitAt(helper, HALF, LOWER_Y, 0.0, Direction.NORTH);
        UUID capId = installGasket(helper, player, upper, vat, GasketRole.RECEIVER, NO_SLOT);
        UUID baseId = installGasket(helper, player, lower, vat, GasketRole.TRANSMITTER, NO_SLOT);

        sneakEmptyHanded(player);
        helper.useBlock(BE_POS, player, lower);
        assertPopped(helper, player, vat, GasketRole.TRANSMITTER, NO_SLOT, baseId);
        BlockState afterPop = helper.getBlockState(BE_POS);
        helper.assertFalse(afterPop.getValue(VatBlock.GASKET_BASE), GASKET_ID_CLEARED);
        helper.assertTrue(afterPop.getValue(VatBlock.GASKET_CAP), OTHER_GASKET_KEPT);
        helper.assertTrue(capId.equals(vat.getGasketId(GasketRole.RECEIVER)), OTHER_GASKET_KEPT);

        helper.useBlock(BE_POS, player, lower);
        helper.assertTrue(afterPop.equals(helper.getBlockState(BE_POS)), STATE_UNCHANGED);
        helper.assertTrue(capId.equals(vat.getGasketId(GasketRole.RECEIVER)), STATE_UNCHANGED);
        helper.assertTrue(gasketsHeld(player) == 1, NO_EXTRA_GASKET);
        helper.succeed();
    }

    // --- Reactor ---

    /**
     * Reactor: sneak empty-hand on the output canister's lower half pops its
     * bottom gasket and keeps the top gasket and the canister; the same click
     * off the hollow leaves the reactor unchanged.
     *
     * @param helper the gametest helper
     */
    public static void reactorPopsHitFaceThenLeavesStateUnchanged(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.REACTOR.get());
        ReactorBlockEntity reactor = helper.getBlockEntity(BE_POS, ReactorBlockEntity.class);
        reactor.insertOutputCanister(new ItemStack(GooItems.CANISTER.get()));
        Player player = playerOnLastHotbarSlot(helper);
        int slot = ReactorBlockEntity.OUTPUT_SLOT;
        UUID topId = installGasket(helper, player, reactorHollowHit(helper, REACTOR_UPPER_Y),
                reactor, GasketRole.RECEIVER, slot);
        UUID bottomId = installGasket(helper, player, reactorHollowHit(helper, LOWER_Y),
                reactor, GasketRole.TRANSMITTER, slot);

        sneakEmptyHanded(player);
        helper.useBlock(BE_POS, player, reactorHollowHit(helper, LOWER_Y));
        assertPopped(helper, player, reactor, GasketRole.TRANSMITTER, slot, bottomId);
        helper.assertTrue(topId.equals(reactor.getGasketId(GasketRole.RECEIVER, slot)), OTHER_GASKET_KEPT);
        helper.assertFalse(reactor.getOutputCanister().isEmpty(), CANISTER_KEPT);

        BlockState before = helper.getBlockState(BE_POS);
        helper.useBlock(BE_POS, player, hitAt(helper, HALF, 1.0, HALF, Direction.UP));
        helper.assertTrue(before.equals(helper.getBlockState(BE_POS)), STATE_UNCHANGED);
        helper.assertFalse(reactor.getOutputCanister().isEmpty(), STATE_UNCHANGED);
        helper.assertTrue(topId.equals(reactor.getGasketId(GasketRole.RECEIVER, slot)), STATE_UNCHANGED);
        helper.assertTrue(gasketsHeld(player) == 1, NO_EXTRA_GASKET);
        helper.succeed();
    }

    // --- Crucible ---

    /**
     * Crucible: sneak empty-hand pops the crucible's gasket and keeps its heat;
     * the same click once the crucible is bare passes (decision fuel-goo-heats-per-mb).
     *
     * @param helper the gametest helper
     */
    public static void cruciblePopsGasketThenPasses(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.CRUCIBLE.get());
        CrucibleBlockEntity crucible = helper.getBlockEntity(BE_POS, CrucibleBlockEntity.class);
        crucible.addHeat(CRUCIBLE_HEAT_TICKS);
        Player player = playerOnLastHotbarSlot(helper);
        BlockHitResult top = hitAt(helper, HALF, 1.0, HALF, Direction.UP);
        UUID gasketId = installGasket(helper, player, top, crucible, GasketRole.TRANSMITTER, NO_SLOT);

        sneakEmptyHanded(player);
        helper.useBlock(BE_POS, player, top);
        assertPopped(helper, player, crucible, GasketRole.TRANSMITTER, NO_SLOT, gasketId);
        helper.assertFalse(helper.getBlockState(BE_POS).getValue(CrucibleBlock.HAS_GASKET), GASKET_ID_CLEARED);
        helper.assertValueEqual(crucible.heatTicks(), CRUCIBLE_HEAT_TICKS, HEAT_KEPT);

        helper.useBlock(BE_POS, player, top);
        helper.assertValueEqual(crucible.heatTicks(), CRUCIBLE_HEAT_TICKS, GASKETLESS_SNEAK_PASSES);
        helper.assertTrue(gasketsHeld(player) == 1, NO_EXTRA_GASKET);
        helper.succeed();
    }

    // --- Helpers ---

    /**
     * Installs a gasket through the gasket item's own click and answers its id.
     *
     * @param helper the gametest helper
     * @param player the player, left holding nothing once the gasket is spent
     * @param hit    the click that installs it
     * @param holder the machine
     * @param role   the face the click installs
     * @param slot   the slot the click installs on, or NO_SLOT
     * @return the installed gasket's id
     */
    private static UUID installGasket(GameTestHelper helper, Player player, BlockHitResult hit,
                                      IGasketHolder holder, GasketRole role, int slot) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.CHORAL_GASKET.get()));
        helper.useBlock(BE_POS, player, hit);
        UUID id = holder.getGasketId(role, slot);
        helper.assertTrue(id != null, GASKET_INSTALLED);
        return id;
    }

    /**
     * A survival player holding the last hotbar slot, so an item the player is
     * handed lands in the first free slot and the hand stays empty between clicks.
     *
     * @param helper the gametest helper
     * @return the player
     */
    private static Player playerOnLastHotbarSlot(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getInventory().setSelectedSlot(Inventory.getSelectionSize() - 1);
        return player;
    }

    private static void sneakEmptyHanded(Player player) {
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setShiftKeyDown(true);
    }

    private static void assertPopped(GameTestHelper helper, Player player, IGasketHolder holder,
                                     GasketRole role, int slot, UUID gasketId) {
        helper.assertTrue(gasketsHeld(player) >= 1, GASKET_IN_HAND);
        helper.assertTrue(holder.getGasketId(role, slot) == null, GASKET_ID_CLEARED);
        helper.assertTrue(GasketRegistry.get(helper.getLevel()).getLocation(gasketId) == null, LOCATION_CLEARED);
    }

    private static int gasketsHeld(Player player) {
        return player.getInventory().countItem(GooItems.CHORAL_GASKET.get());
    }

    private static BlockHitResult hitAt(GameTestHelper helper, double x, double y, double z, Direction face) {
        BlockPos abs = helper.absolutePos(BE_POS);
        return new BlockHitResult(new Vec3(abs.getX() + x, abs.getY() + y, abs.getZ() + z), face, abs, false);
    }

    private static BlockHitResult reactorHollowHit(GameTestHelper helper, double localY) {
        Direction facing = helper.getBlockState(BE_POS).getValue(ReactorBlock.FACING);
        AABB slot = ReactorBlock.outputSlotShape(facing).bounds();
        return hitAt(helper, (slot.minX + slot.maxX) * HALF, localY, (slot.minZ + slot.maxZ) * HALF, facing);
    }
}
