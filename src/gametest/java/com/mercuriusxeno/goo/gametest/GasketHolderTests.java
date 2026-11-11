package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.GooConstants;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mercuriusxeno.goo.block.canister.CanisterBlock;
import com.mercuriusxeno.goo.block.canister.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.crucible.CrucibleBlockEntity;
import com.mercuriusxeno.goo.block.gasket.IGasketHolder;
import com.mercuriusxeno.goo.block.hub.HubBlock;
import com.mercuriusxeno.goo.block.hub.HubBlockEntity;
import com.mercuriusxeno.goo.block.reactor.ReactorBlock;
import com.mercuriusxeno.goo.block.reactor.ReactorBlockEntity;
import com.mercuriusxeno.goo.data.GasketLocation;
import com.mercuriusxeno.goo.data.GasketRegistry;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.gasket.GasketPartner;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooCapabilities;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
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
import java.util.Map;
import java.util.UUID;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for {@link IGasketHolder} contracts on real block entities.
 * Replaces the stub-based JUnit tests that used StubHolder, VatStub,
 * CrucibleStub, and HubStub to avoid Minecraft class initialization.
 */
@RegistersGameTests
public final class GasketHolderTests {
    private static final String REACTOR_GASKET_INSTALL = "reactor_gasket_install";
    private static final String REACTOR_TUNER_LINK = "reactor_tuner_link";
    private static final String CRUCIBLE_GOO_GASKET_TUNER = "crucible_goo_gasket_tuner";
    private static final String REACTOR_GASKET_LOCATION = "reactor_gasket_location";
    private static final String HUB_SLOT_TRANSMITS = "hub_slot_transmits";
    private static final String HUB_SLOT_MISS_INTAKE = "hub_slot_miss_intake";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, REACTOR_GASKET_INSTALL, GasketHolderTests::reactorGasketInstallsOnOutputCanister);
        GooTestFunctions.registerTest(registrar, REACTOR_TUNER_LINK, GasketHolderTests::reactorTunerLinksCrucibleToOutputCanister);
        GooTestFunctions.registerTest(registrar, CRUCIBLE_GOO_GASKET_TUNER, GasketHolderTests::crucibleHoldingGooTakesGasketAndTuner);
        GooTestFunctions.registerTest(registrar, REACTOR_GASKET_LOCATION, GasketHolderTests::reactorOutputGasketLocationFollowsCanister);
        GooTestFunctions.registerTest(registrar, HUB_SLOT_TRANSMITS, GasketHolderTests::hubSlotLinksAsTransmitter);
        GooTestFunctions.registerTest(registrar, HUB_SLOT_MISS_INTAKE, GasketHolderTests::hubSlotMissKeepsIntakePath);
    }


    private static final BlockPos BE_POS = new BlockPos(1, 1, 1);

    // --- Reactor fixtures ---

    private static final BlockPos CRUCIBLE_POS = new BlockPos(3, 1, 1);
    /** Below the output canister's vertical midpoint: resolves the bottom (transmitter) face. */
    private static final double LOWER_HOLLOW_Y = 0.25;
    /** Above the output canister's vertical midpoint: resolves the top (receiver) face. */
    private static final double UPPER_HOLLOW_Y = 0.65;
    private static final double HALF = 0.5;
    /** Rock goo seeded in the crucible before the gasket and tuner clicks. */
    private static final int CRUCIBLE_GOO_VOLUME = 1000;
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

    // --- Hub fixtures ---

    /** Where a hub test stands the canister its slot links to. */
    private static final BlockPos PARTNER_POS = new BlockPos(1, 1, 3);

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
    private static final String HUB_HIT_MISSES = "Hub center hit should miss every slot";
    private static final String HUB_MISS_NO_INTAKE =
            "Slot miss on a hub without intake gasket should report the missing intake gasket; feedback was ";
    private static final String HUB_MISS_UNLINKED = "Slot miss should link nothing to the intake";

    private GasketHolderTests() {
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
        helper.setBlock(PARTNER_POS, GooBlocks.CANISTER.get());
        CanisterBlockEntity receiver = helper.getBlockEntity(PARTNER_POS, CanisterBlockEntity.class);
        receiver.insertCanister(CanisterBlock.CENTER_SLOT, gasketedCanister(), false);
        helper.setBlock(BE_POS, GooBlocks.HUB.get());
        HubBlockEntity hub = helper.getBlockEntity(BE_POS, HubBlockEntity.class);
        hub.insertCanister(HUB_TEST_SLOT, gasketedCanister());

        ServerPlayer player = serverPlayerHoldingTuner(helper);
        TunerFeedbackRecorder recorder = TunerFeedbackRecorder.attachTo(player);
        BlockPos receiverAbs = helper.absolutePos(PARTNER_POS);
        helper.useBlock(PARTNER_POS, player, new BlockHitResult(
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

    /**
     * Crucible holding goo: a gasket click installs the crucible's gasket and a tuner click
     * starts the link that the reactor's output canister then completes, and neither click
     * drains the reservoir, since the dispatcher passes both to the item before any
     * empty-hand fallthrough (decision every-machine-clicks-through-the-dispatcher).
     *
     * @param helper the gametest helper
     */
    public static void crucibleHoldingGooTakesGasketAndTuner(GameTestHelper helper) {
        ReactorBlockEntity reactor = placeReactorWithOutputCanister(helper);
        Player player = playerHolding(helper, GooItems.CHORAL_GASKET.get());
        helper.useBlock(BE_POS, player, reactorHollowHit(helper, UPPER_HOLLOW_Y));
        UUID reactorGasket = CanisterItem.getMetadata(reactor.getOutputCanister()).topGasketId();
        helper.assertTrue(reactorGasket != null, REACTOR_TOP_GASKET);

        helper.setBlock(CRUCIBLE_POS, GooBlocks.CRUCIBLE.get());
        CrucibleBlockEntity crucible = helper.getBlockEntity(CRUCIBLE_POS, CrucibleBlockEntity.class);
        crucible.insertGoo(GooTypes.ROCK, CRUCIBLE_GOO_VOLUME);
        Map<ResourceKey<GooTypeDefinition>, Integer> before = crucible.getReservoir().getAll();
        helper.assertFalse(before.isEmpty(), "The crucible should hold goo before the clicks");

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.CHORAL_GASKET.get()));
        helper.useBlock(CRUCIBLE_POS, player, centerHit(helper, CRUCIBLE_POS));
        UUID crucibleGasket = crucible.getGasketId(GasketRole.TRANSMITTER);
        helper.assertTrue(crucibleGasket != null, CRUCIBLE_GASKET_PRESENT);

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.CHORAL_TUNER.get()));
        helper.useBlock(CRUCIBLE_POS, player, centerHit(helper, CRUCIBLE_POS));
        helper.useBlock(BE_POS, player, reactorHollowHit(helper, UPPER_HOLLOW_Y));

        helper.assertTrue(reactorGasket.equals(GasketRegistry.get(helper.getLevel()).getTarget(crucibleGasket)),
                REACTOR_LINKED);
        helper.assertTrue(before.equals(crucible.getReservoir().getAll()),
                "Gasket and tuner clicks should leave the crucible's goo untouched");
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
