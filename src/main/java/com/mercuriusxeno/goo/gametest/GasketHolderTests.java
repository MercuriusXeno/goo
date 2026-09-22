package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.block.crucible.CrucibleBlock;
import com.mercuriusxeno.goo.block.crucible.CrucibleBlockEntity;
import com.mercuriusxeno.goo.block.gasket.IGasketHolder;
import com.mercuriusxeno.goo.block.hub.HubBlockEntity;
import com.mercuriusxeno.goo.block.reactor.ReactorBlock;
import com.mercuriusxeno.goo.block.reactor.ReactorBlockEntity;
import com.mercuriusxeno.goo.block.vat.VatBlock;
import com.mercuriusxeno.goo.block.vat.VatBlockEntity;
import com.mercuriusxeno.goo.data.GasketLocation;
import com.mercuriusxeno.goo.data.GasketRegistry;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.CanisterMetadata;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooCapabilities;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
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
