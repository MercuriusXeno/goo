package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.GooConstants;
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
import com.mercuriusxeno.goo.data.GasketLocation;
import com.mercuriusxeno.goo.data.GasketRegistry;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.CanisterMetadata;
import com.mercuriusxeno.goo.item.gasket.GasketPartner;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooCapabilities;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.UUID;

/**
 * Gametests for {@link IGasketHolder} contracts on real block entities.
 * Replaces the stub-based JUnit tests that used StubHolder, VatStub,
 * CrucibleStub, and HubStub to avoid Minecraft class initialization.
 */
public final class GasketHolderTests {

    private static final BlockPos BE_POS = new BlockPos(1, 1, 1);
    private static final String CRUCIBLE_TRANSMITTER = "Crucible resolveRole should always return TRANSMITTER";
    private static final String NO_GASKET_TX = "Crucible without gasket should not support TRANSMITTER";
    private static final String NO_GASKET_RX = "Crucible without gasket should not support RECEIVER";
    private static final String WITH_GASKET_TX = "Crucible with gasket should support TRANSMITTER";
    private static final String VAT_CAP_RX = "Vat with cap should support RECEIVER";
    private static final String VAT_NO_BASE_TX = "Vat without base should not support TRANSMITTER";
    private static final String HUB_INTAKE = "Hub should always have intake";
    private static final String TUNING_NULL = "Default allowsTuning(null) should be true";
    private static final String TUNING_RANDOM = "Default allowsTuning(random) should be true";

    // --- Reactor fixtures ---

    private static final BlockPos CRUCIBLE_POS = new BlockPos(3, 1, 1);
    /** Below the output canister's vertical midpoint: resolves the bottom (transmitter) face. */
    private static final double LOWER_HOLLOW_Y = 0.25;
    /** Above the output canister's vertical midpoint: resolves the top (receiver) face. */
    private static final double UPPER_HOLLOW_Y = 0.65;
    private static final double HALF = 0.5;
    private static final String REACTOR_CANISTER_KEPT =
            "Gasket click on the reactor hollow should leave the output canister in place";
    private static final String REACTOR_BOTTOM_GASKET =
            "Output canister metadata should carry a bottom gasket id after install";
    private static final String REACTOR_TOP_GASKET =
            "Output canister metadata should carry a top gasket id after install";
    private static final String CRUCIBLE_GASKET_PRESENT = "Crucible should hold a transmitter gasket id";
    private static final String REACTOR_LINKED = "Registry should pair the crucible gasket to the reactor canister gasket";
    private static final String REACTOR_CAPABILITY = "Reactor should answer GASKET_BLOCK for its canister gasket id";
    private static final String REACTOR_LOCATION_CLEARED =
            "Removing the output canister should clear its gasket location";
    private static final String REACTOR_CANISTER_IN_HAND = "Removed canister should reach the player's inventory";
    private static final String REACTOR_LOCATION_RESTORED =
            "Re-inserting the output canister should register its gasket at the reactor's OUTPUT_SLOT";
    private static final String REACTOR_SEATED_TOP_GASKET =
            "Seated canister metadata should answer the top gasket id it was inserted with";
    private static final String REACTOR_SEATED_BOTTOM_GASKET =
            "Seated canister metadata should answer the bottom gasket id it was inserted with";

    // --- Tap fixtures ---

    private static final BlockPos TAP_PARTNER_POS = new BlockPos(1, 1, 3);
    /** Inside the tap body, below the block's vertical midpoint. */
    private static final double TAP_BODY_Y = 2.0 / 16.0;
    /** Inside the inserted canister, above the block's vertical midpoint. */
    private static final double TAP_CANISTER_Y = 10.0 / 16.0;
    private static final String TAP_BODY_RECEIVER = "Gasketed tap should resolve RECEIVER for a hit on its body";
    private static final String TAP_CANISTER_RECEIVER =
            "Gasketed tap should resolve RECEIVER for a hit on its inserted canister";
    private static final String TAP_GASKET_INSTALLED = "Choral gasket used on the tap should install its gasket";
    private static final String TAP_LINKED_RECEIVER =
            "Tuner should link the tap as receiver of the canister's transmitter gasket";
    private static final String TAP_GASKET_NO_TX = "Gasketed tap should not support TRANSMITTER";
    private static final String TAP_NO_GASKET_TX = "Tap without gasket should not support TRANSMITTER";
    private static final String TAP_NO_GASKET_RX = "Tap without gasket should not support RECEIVER";

    // --- Hub fixtures ---

    private static final String REMOVAL = "removal";
    private static final int HUB_TEST_SLOT = 0;
    private static final double PIXELS_PER_BLOCK = 16.0;
    /** The tuner's refusal of a role reads "<Face> can't be a <role>". */
    private static final String MSG_CANT_BE = "can't be a";
    private static final String MSG_NO_INTAKE_GASKET = "No gasket on intake";
    private static final String HUB_SLOT_NOT_REFUSED =
            "Tuner should not refuse the transmitter role on a hub slot canister; feedback was ";
    private static final String HUB_SLOT_LINKED =
            "Tuner should link the hub slot's bottom gasket as transmitter to the canister receiver";
    private static final String HUB_SUPPORTS_TX = "Hub should support TRANSMITTER for its slot canisters";
    private static final String HUB_SUPPORTS_RX = "Hub should support RECEIVER";
    private static final String HUB_INTAKE_NO_TX = "Hub intake should never hold a TRANSMITTER gasket";
    private static final String HUB_INTAKE_RX = "Hub with HAS_GASKET should hold its RECEIVER intake gasket";
    private static final String HUB_HIT_MISSES = "Hub center hit should miss every slot";
    private static final String HUB_MISS_NO_INTAKE =
            "Slot miss on a hub without intake gasket should report the missing intake gasket; feedback was ";
    private static final String HUB_MISS_UNLINKED = "Slot miss should link nothing to the intake";

    private GasketHolderTests() {
    }

    // --- Crucible ---

    /**
     * Crucible always resolves as TRANSMITTER regardless of hit location.
     *
     * @param helper the gametest helper
     */
    public static void crucibleResolveRoleAlwaysTransmitter(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.CRUCIBLE.get());
        CrucibleBlockEntity be = helper.getBlockEntity(BE_POS, CrucibleBlockEntity.class);
        helper.assertTrue(be.resolveRole(null) == GasketRole.TRANSMITTER, CRUCIBLE_TRANSMITTER);
        helper.succeed();
    }

    /**
     * Crucible supportsRole returns false when HAS_GASKET blockstate is false.
     *
     * @param helper the gametest helper
     */
    public static void crucibleNoGasketUnsupported(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.CRUCIBLE.get().defaultBlockState()
                .setValue(CrucibleBlock.HAS_GASKET, false));
        CrucibleBlockEntity be = helper.getBlockEntity(BE_POS, CrucibleBlockEntity.class);
        helper.assertFalse(be.supportsRole(GasketRole.TRANSMITTER), NO_GASKET_TX);
        helper.assertFalse(be.supportsRole(GasketRole.RECEIVER), NO_GASKET_RX);
        helper.succeed();
    }

    /**
     * Crucible supportsRole returns true when HAS_GASKET blockstate is true.
     *
     * @param helper the gametest helper
     */
    public static void crucibleWithGasketSupported(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.CRUCIBLE.get().defaultBlockState()
                .setValue(CrucibleBlock.HAS_GASKET, true));
        CrucibleBlockEntity be = helper.getBlockEntity(BE_POS, CrucibleBlockEntity.class);
        helper.assertTrue(be.supportsRole(GasketRole.TRANSMITTER), WITH_GASKET_TX);
        helper.succeed();
    }

    // --- Vat ---

    /**
     * Vat supportsRole reflects GASKET_CAP and GASKET_BASE blockstate properties.
     *
     * @param helper the gametest helper
     */
    public static void vatSupportsRoleMatchesBlockstate(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.VAT.get().defaultBlockState()
                .setValue(VatBlock.GASKET_CAP, true)
                .setValue(VatBlock.GASKET_BASE, false));
        VatBlockEntity be = helper.getBlockEntity(BE_POS, VatBlockEntity.class);
        helper.assertTrue(be.supportsRole(GasketRole.RECEIVER), VAT_CAP_RX);
        helper.assertFalse(be.supportsRole(GasketRole.TRANSMITTER), VAT_NO_BASE_TX);
        helper.succeed();
    }

    // --- Tap (diagnose-then-fix-tap-gasket-role) ---

    /**
     * Tap: a gasketed tap resolves RECEIVER for a hit on its body, below the
     * block's vertical midpoint, and for a hit on its inserted canister above it.
     *
     * @param helper the gametest helper
     */
    public static void tapResolveRoleAlwaysReceiver(GameTestHelper helper) {
        TapBlockEntity tap = placeGasketedTap(helper);
        tap.insertCanister(new ItemStack(GooItems.CANISTER.get()));
        helper.assertTrue(tap.resolveRole(tapHit(helper, TAP_BODY_Y)) == GasketRole.RECEIVER, TAP_BODY_RECEIVER);
        helper.assertTrue(tap.resolveRole(tapHit(helper, TAP_CANISTER_Y)) == GasketRole.RECEIVER,
                TAP_CANISTER_RECEIVER);
        helper.succeed();
    }

    /**
     * Tap: a tuner holding a canister's transmitter link, used on a gasketed
     * tap's body, links the tap as the receiver of that canister.
     *
     * @param helper the gametest helper
     */
    public static void tapTunerLinksCanisterTransmitter(GameTestHelper helper) {
        helper.setBlock(TAP_PARTNER_POS, GooBlocks.CANISTER.get());
        CanisterBlockEntity canister = helper.getBlockEntity(TAP_PARTNER_POS, CanisterBlockEntity.class);
        ItemStack held = new ItemStack(GooItems.CANISTER.get());
        CanisterItem.setMetadata(held, CanisterItem.getMetadata(held).withGasketIds());
        canister.insertCanister(CanisterBlock.CENTER_SLOT, held, false);
        helper.setBlock(BE_POS, GooBlocks.TAP.get());
        TapBlockEntity tap = helper.getBlockEntity(BE_POS, TapBlockEntity.class);
        Player player = playerHolding(helper, GooItems.CHORAL_GASKET.get());
        helper.useBlock(BE_POS, player, tapHit(helper, TAP_BODY_Y));
        helper.assertTrue(helper.getBlockState(BE_POS).getValue(TapBlock.HAS_GASKET), TAP_GASKET_INSTALLED);

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.CHORAL_TUNER.get()));
        BlockPos canisterAbs = helper.absolutePos(TAP_PARTNER_POS);
        helper.useBlock(TAP_PARTNER_POS, player, new BlockHitResult(
                new Vec3(canisterAbs.getX() + HALF, canisterAbs.getY() + LOWER_HOLLOW_Y, canisterAbs.getZ() + HALF),
                Direction.UP, canisterAbs, false));
        helper.useBlock(BE_POS, player, tapHit(helper, TAP_BODY_Y));

        GasketPartner partner = tap.getPartner(GasketRole.RECEIVER, GooConstants.NO_SLOT);
        helper.assertTrue(partner != null && canisterAbs.equals(partner.pos())
                && partner.slot() == CanisterBlock.CENTER_SLOT, TAP_LINKED_RECEIVER);
        helper.succeed();
    }

    /**
     * Tap: a gasketed tap with no canister refuses the transmitter role, and a
     * tap without a gasket refuses both roles.
     *
     * @param helper the gametest helper
     */
    public static void tapRefusesTransmitterRole(GameTestHelper helper) {
        TapBlockEntity gasketed = placeGasketedTap(helper);
        helper.assertFalse(gasketed.supportsRole(GasketRole.TRANSMITTER), TAP_GASKET_NO_TX);
        helper.setBlock(BE_POS, GooBlocks.TAP.get().defaultBlockState().setValue(TapBlock.HAS_GASKET, false));
        TapBlockEntity bare = helper.getBlockEntity(BE_POS, TapBlockEntity.class);
        helper.assertFalse(bare.supportsRole(GasketRole.TRANSMITTER), TAP_NO_GASKET_TX);
        helper.assertFalse(bare.supportsRole(GasketRole.RECEIVER), TAP_NO_GASKET_RX);
        helper.succeed();
    }

    private static TapBlockEntity placeGasketedTap(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.TAP.get().defaultBlockState().setValue(TapBlock.HAS_GASKET, true));
        return helper.getBlockEntity(BE_POS, TapBlockEntity.class);
    }

    /**
     * A hit centered on the tap body's footprint at the given block-local height.
     *
     * @param helper the gametest helper
     * @param localY block-local Y of the hit
     * @return the hit result
     */
    private static BlockHitResult tapHit(GameTestHelper helper, double localY) {
        BlockPos abs = helper.absolutePos(BE_POS);
        Direction facing = helper.getBlockState(BE_POS).getValue(TapBlock.FACING);
        AABB body = TapBlock.bodyShape(facing).bounds();
        Vec3 location = new Vec3(
                abs.getX() + (body.minX + body.maxX) * HALF,
                abs.getY() + localY,
                abs.getZ() + (body.minZ + body.maxZ) * HALF);
        return new BlockHitResult(location, facing, abs, false);
    }

    // --- Hub ---

    /**
     * Hub always reports hasIntake() = true.
     *
     * @param helper the gametest helper
     */
    public static void hubHasIntake(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.HUB.get());
        HubBlockEntity be = helper.getBlockEntity(BE_POS, HubBlockEntity.class);
        helper.assertTrue(be.hasIntake(), HUB_INTAKE);
        helper.succeed();
    }

    // --- Hub (diagnose-then-fix-hub-canister-transmitter) ---

    /**
     * Hub: a tuner holding a canister's receiver link, used on the lower half of a
     * hub slot whose canister carries a bottom gasket, links that slot as transmitter
     * and sends no "can't be a transmitter" feedback.
     *
     * @param helper the gametest helper
     */
    public static void hubSlotLinksAsTransmitter(GameTestHelper helper) {
        helper.setBlock(TAP_PARTNER_POS, GooBlocks.CANISTER.get());
        CanisterBlockEntity receiver = helper.getBlockEntity(TAP_PARTNER_POS, CanisterBlockEntity.class);
        receiver.insertCanister(CanisterBlock.CENTER_SLOT, gasketedCanister(), false);
        helper.setBlock(BE_POS, GooBlocks.HUB.get());
        HubBlockEntity hub = helper.getBlockEntity(BE_POS, HubBlockEntity.class);
        hub.insertCanister(HUB_TEST_SLOT, gasketedCanister());

        ServerPlayer player = serverPlayerHoldingTuner(helper);
        TunerFeedbackRecorder recorder = TunerFeedbackRecorder.attachTo(player);
        BlockPos receiverAbs = helper.absolutePos(TAP_PARTNER_POS);
        helper.useBlock(TAP_PARTNER_POS, player, new BlockHitResult(
                new Vec3(receiverAbs.getX() + HALF, receiverAbs.getY() + UPPER_HOLLOW_Y, receiverAbs.getZ() + HALF),
                Direction.UP, receiverAbs, false));
        helper.useBlock(BE_POS, player, hubSlotHit(helper, HUB_TEST_SLOT, LOWER_HOLLOW_Y));

        List<String> feedback = recorder.lines();
        helper.assertTrue(feedback.stream().noneMatch(line -> line.contains(MSG_CANT_BE)),
                HUB_SLOT_NOT_REFUSED + feedback);
        GasketPartner partner = hub.getPartner(GasketRole.TRANSMITTER, HUB_TEST_SLOT);
        helper.assertTrue(partner != null && receiverAbs.equals(partner.pos())
                && partner.slot() == CanisterBlock.CENTER_SLOT, HUB_SLOT_LINKED);
        helper.succeed();
    }

    /**
     * Hub: supports both roles for its slot canisters while its block-level
     * gasket stays the receiver intake.
     *
     * @param helper the gametest helper
     */
    public static void hubSupportsBothRolesIntakeReceives(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.HUB.get().defaultBlockState().setValue(HubBlock.HAS_GASKET, true));
        HubBlockEntity hub = helper.getBlockEntity(BE_POS, HubBlockEntity.class);
        helper.assertTrue(hub.supportsRole(GasketRole.TRANSMITTER), HUB_SUPPORTS_TX);
        helper.assertTrue(hub.supportsRole(GasketRole.RECEIVER), HUB_SUPPORTS_RX);
        helper.assertFalse(hub.holdsBlockGasket(GasketRole.TRANSMITTER), HUB_INTAKE_NO_TX);
        helper.assertTrue(hub.holdsBlockGasket(GasketRole.RECEIVER), HUB_INTAKE_RX);
        helper.succeed();
    }

    /**
     * Hub: a tuner click on the hub body that misses every slot keeps the intake
     * path, so a hub with no intake gasket links nothing and reports the missing
     * intake gasket.
     *
     * @param helper the gametest helper
     */
    public static void hubSlotMissKeepsIntakePath(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.HUB.get());
        HubBlockEntity hub = helper.getBlockEntity(BE_POS, HubBlockEntity.class);
        BlockHitResult miss = centerHitAt(helper, BE_POS, LOWER_HOLLOW_Y);
        helper.assertTrue(hub.resolveSlot(miss) == IGasketHolder.SLOT_MISS, HUB_HIT_MISSES);

        ServerPlayer player = serverPlayerHoldingTuner(helper);
        TunerFeedbackRecorder recorder = TunerFeedbackRecorder.attachTo(player);
        helper.useBlock(BE_POS, player, miss);

        List<String> feedback = recorder.lines();
        helper.assertTrue(feedback.contains(MSG_NO_INTAKE_GASKET), HUB_MISS_NO_INTAKE + feedback);
        helper.assertTrue(hub.getPartner(GasketRole.RECEIVER, GooConstants.NO_SLOT) == null, HUB_MISS_UNLINKED);
        helper.succeed();
    }

    @SuppressWarnings(REMOVAL) // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer serverPlayerHoldingTuner(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.CHORAL_TUNER.get()));
        return player;
    }

    private static ItemStack gasketedCanister() {
        ItemStack canister = new ItemStack(GooItems.CANISTER.get());
        CanisterItem.setMetadata(canister, CanisterItem.getMetadata(canister).withGasketIds());
        return canister;
    }

    /**
     * A hit on a hub slot's center at the given block-local height.
     *
     * @param helper the gametest helper
     * @param slot   the hub slot index
     * @param localY block-local Y of the hit
     * @return the hit result
     */
    private static BlockHitResult hubSlotHit(GameTestHelper helper, int slot, double localY) {
        BlockPos abs = helper.absolutePos(BE_POS);
        Vec3 location = new Vec3(
                abs.getX() + HubBlock.SLOT_CENTERS[slot][0] / PIXELS_PER_BLOCK,
                abs.getY() + localY,
                abs.getZ() + HubBlock.SLOT_CENTERS[slot][1] / PIXELS_PER_BLOCK);
        return new BlockHitResult(location, Direction.UP, abs, false);
    }

    private static BlockHitResult centerHitAt(GameTestHelper helper, BlockPos pos, double localY) {
        BlockPos abs = helper.absolutePos(pos);
        return new BlockHitResult(new Vec3(abs.getX() + HALF, abs.getY() + localY, abs.getZ() + HALF),
                Direction.UP, abs, false);
    }

    // --- Default contract ---

    /**
     * IGasketHolder.allowsTuning defaults to true for all machines.
     * Verified on a crucible (no ownership model).
     *
     * @param helper the gametest helper
     */
    public static void defaultAllowsTuningIsTrue(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.CRUCIBLE.get());
        CrucibleBlockEntity be = helper.getBlockEntity(BE_POS, CrucibleBlockEntity.class);
        helper.assertTrue(be.allowsTuning(null), TUNING_NULL);
        helper.assertTrue(be.allowsTuning(java.util.UUID.randomUUID()), TUNING_RANDOM);
        helper.succeed();
    }

    // --- Reactor (reactor-gasket-click-fix) ---

    /**
     * Reactor: a choral gasket used on the lower half of the output hollow installs
     * a bottom gasket on the held canister and leaves the canister in place.
     *
     * @param helper the gametest helper
     */
    public static void reactorGasketInstallsOnOutputCanister(GameTestHelper helper) {
        ReactorBlockEntity reactor = placeReactorWithOutputCanister(helper);
        Player player = playerHolding(helper, GooItems.CHORAL_GASKET.get());
        helper.useBlock(BE_POS, player, reactorHollowHit(helper, LOWER_HOLLOW_Y));

        ItemStack output = reactor.getOutputCanister();
        helper.assertFalse(output.isEmpty(), REACTOR_CANISTER_KEPT);
        helper.assertTrue(CanisterItem.getMetadata(output).bottomGasketId() != null, REACTOR_BOTTOM_GASKET);
        helper.succeed();
    }

    /**
     * Reactor: the choral tuner used on a crucible transmitter then on the reactor's
     * top-gasketed output canister links the two, and the reactor answers the
     * GASKET_BLOCK capability for that gasket id.
     *
     * @param helper the gametest helper
     */
    public static void reactorTunerLinksCrucibleToOutputCanister(GameTestHelper helper) {
        ReactorBlockEntity reactor = placeReactorWithOutputCanister(helper);
        Player player = playerHolding(helper, GooItems.CHORAL_GASKET.get());
        helper.useBlock(BE_POS, player, reactorHollowHit(helper, UPPER_HOLLOW_Y));
        UUID reactorGasket = CanisterItem.getMetadata(reactor.getOutputCanister()).topGasketId();
        helper.assertTrue(reactorGasket != null, REACTOR_TOP_GASKET);
        UUID crucibleGasket = installCrucibleTransmitter(helper, player);

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.CHORAL_TUNER.get()));
        helper.useBlock(CRUCIBLE_POS, player, centerHit(helper, CRUCIBLE_POS));
        helper.useBlock(BE_POS, player, reactorHollowHit(helper, UPPER_HOLLOW_Y));

        GasketRegistry registry = GasketRegistry.get(helper.getLevel());
        helper.assertTrue(reactorGasket.equals(registry.getTarget(crucibleGasket)), REACTOR_LINKED);
        var handler = helper.getLevel().getCapability(
                GooCapabilities.GASKET_BLOCK, helper.absolutePos(BE_POS), reactorGasket);
        helper.assertTrue(handler != null, REACTOR_CAPABILITY);
        helper.succeed();
    }

    /**
     * Reactor: removing the gasket-bearing output canister with an empty hand clears
     * its registry location, and re-inserting it registers the reactor's OUTPUT_SLOT.
     *
     * @param helper the gametest helper
     */
    public static void reactorOutputGasketLocationFollowsCanister(GameTestHelper helper) {
        ReactorBlockEntity reactor = placeReactorWithOutputCanister(helper);
        Player player = playerHolding(helper, GooItems.CHORAL_GASKET.get());
        helper.useBlock(BE_POS, player, reactorHollowHit(helper, LOWER_HOLLOW_Y));
        UUID gasketId = CanisterItem.getMetadata(reactor.getOutputCanister()).bottomGasketId();
        helper.assertTrue(gasketId != null, REACTOR_BOTTOM_GASKET);
        GasketRegistry registry = GasketRegistry.get(helper.getLevel());

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.useBlock(BE_POS, player, reactorHollowHit(helper, LOWER_HOLLOW_Y));
        helper.assertTrue(registry.getLocation(gasketId) == null, REACTOR_LOCATION_CLEARED);

        ItemStack removed = canisterInInventory(player, gasketId);
        helper.assertFalse(removed.isEmpty(), REACTOR_CANISTER_IN_HAND);
        player.setItemInHand(InteractionHand.MAIN_HAND, removed);
        helper.useBlock(BE_POS, player, reactorHollowHit(helper, LOWER_HOLLOW_Y));
        GasketLocation expected = new GasketLocation(helper.getLevel().dimension(),
                helper.absolutePos(BE_POS), false, ReactorBlockEntity.OUTPUT_SLOT);
        helper.assertTrue(expected.equals(registry.getLocation(gasketId)), REACTOR_LOCATION_RESTORED);
        helper.succeed();
    }

    // --- Reactor (reactor-gasket-render-fix) ---

    /**
     * Reactor: a canister seated with a choral gasket on each end answers both
     * gasket ids through {@link CanisterItem#getMetadata}, the read the renderer's
     * extract makes to choose the cap texture.
     *
     * @param helper the gametest helper
     */
    public static void reactorSeatedCanisterAnswersGasketMetadata(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.REACTOR.get());
        ReactorBlockEntity reactor = helper.getBlockEntity(BE_POS, ReactorBlockEntity.class);
        UUID topId = UUID.randomUUID();
        UUID bottomId = UUID.randomUUID();
        ItemStack canister = new ItemStack(GooItems.CANISTER.get());
        CanisterItem.setMetadata(canister,
                CanisterItem.getMetadata(canister).withTopGasketId(topId).withBottomGasketId(bottomId));
        reactor.insertOutputCanister(canister);

        CanisterMetadata seated = CanisterItem.getMetadata(reactor.getOutputCanister());
        helper.assertTrue(topId.equals(seated.topGasketId()), REACTOR_SEATED_TOP_GASKET);
        helper.assertTrue(bottomId.equals(seated.bottomGasketId()), REACTOR_SEATED_BOTTOM_GASKET);
        helper.succeed();
    }

    // --- Reactor helpers ---

    private static ReactorBlockEntity placeReactorWithOutputCanister(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.REACTOR.get());
        ReactorBlockEntity reactor = helper.getBlockEntity(BE_POS, ReactorBlockEntity.class);
        reactor.insertOutputCanister(new ItemStack(GooItems.CANISTER.get()));
        return reactor;
    }

    private static Player playerHolding(GameTestHelper helper, Item item) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
        return player;
    }

    /**
     * A hit on the reactor's output canister box at the given block-local height,
     * centered on the slot shape so the hollow and slot checks both see it.
     *
     * @param helper the gametest helper
     * @param localY block-local Y of the hit
     * @return the hit result
     */
    private static BlockHitResult reactorHollowHit(GameTestHelper helper, double localY) {
        BlockPos abs = helper.absolutePos(BE_POS);
        Direction facing = helper.getBlockState(BE_POS).getValue(ReactorBlock.FACING);
        AABB slot = ReactorBlock.outputSlotShape(facing).bounds();
        Vec3 location = new Vec3(
                abs.getX() + (slot.minX + slot.maxX) * HALF,
                abs.getY() + localY,
                abs.getZ() + (slot.minZ + slot.maxZ) * HALF);
        return new BlockHitResult(location, facing, abs, false);
    }

    private static BlockHitResult centerHit(GameTestHelper helper, BlockPos pos) {
        BlockPos abs = helper.absolutePos(pos);
        return new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
    }

    /**
     * Places a crucible and installs a choral gasket on it through the item's use path.
     *
     * @param helper the gametest helper
     * @param player the player, left holding the gasket item
     * @return the crucible's transmitter gasket id
     */
    private static UUID installCrucibleTransmitter(GameTestHelper helper, Player player) {
        helper.setBlock(CRUCIBLE_POS, GooBlocks.CRUCIBLE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.CHORAL_GASKET.get()));
        helper.useBlock(CRUCIBLE_POS, player, centerHit(helper, CRUCIBLE_POS));
        CrucibleBlockEntity crucible = helper.getBlockEntity(CRUCIBLE_POS, CrucibleBlockEntity.class);
        UUID gasket = crucible.getGasketId(GasketRole.TRANSMITTER);
        helper.assertTrue(gasket != null, CRUCIBLE_GASKET_PRESENT);
        return gasket;
    }

    private static ItemStack canisterInInventory(Player player, UUID gasketId) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (gasketId.equals(CanisterItem.getMetadata(stack).bottomGasketId())) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
}
