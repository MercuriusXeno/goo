package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.block.canister.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.hub.HubBlockEntity;
import com.mercuriusxeno.goo.block.plexer.PlexerBlockEntity;
import com.mercuriusxeno.goo.block.reactor.ReactorBlockEntity;
import com.mercuriusxeno.goo.data.GasketRegistry;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.data.GooValues;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooFluids;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.slf4j.Logger;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Gametests for machine block entities. Exercises placement, slot lifecycle,
 * tick loops, and serialization on real BEs without stubs or mocks.
 */
@RegistersGameTests
public final class MachineTests {
    private static final String MACHINE_CANISTER_BREAK_RELEASES_GASKET = "machine_canister_break_releases_gasket";
    private static final String MACHINE_HUB_MIXED_EXTRACT = "machine_hub_mixed_extract";
    private static final String MACHINE_MIXED_CANISTER_SOURCE = "machine_mixed_canister_source";
    private static final String MACHINE_CANISTER_ROUNDTRIP = "machine_canister_roundtrip";
    private static final String MACHINE_REACTOR_BREAK_RELEASES_GASKET = "machine_reactor_break_releases_gasket";
    private static final String MACHINE_REACTOR_REACTION = "machine_reactor_reaction";
    private static final String MACHINE_REACTOR_REDSTONE = "machine_reactor_redstone";
    private static final String MACHINE_PLEXER_RECONSTITUTES = "machine_plexer_reconstitutes";
    private static final String STONE_HAS_VALUE = "Stone should hold a goo value for the plexer to fabricate";
    private static final String PLEXER_MADE_NOTHING = "A plexer with its target's goo above should reconstitute the target";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, MACHINE_CANISTER_BREAK_RELEASES_GASKET, MachineTests::canisterBreakReleasesSlotGasket);
        GooTestFunctions.registerTest(registrar, MACHINE_HUB_MIXED_EXTRACT, MachineTests::hubExtractsOneTypeFromAMixedCanister);
        GooTestFunctions.registerTest(registrar, MACHINE_MIXED_CANISTER_SOURCE, MachineTests::mixedCanisterIsAGooSourceForEachType);
        GooTestFunctions.registerTest(registrar, MACHINE_CANISTER_ROUNDTRIP, MachineTests::canisterSurvivesRoundTrip);
        GooTestFunctions.registerTest(registrar, MACHINE_REACTOR_BREAK_RELEASES_GASKET, MachineTests::reactorBreakReleasesOutputGasket);
        GooTestFunctions.registerTest(registrar, MACHINE_REACTOR_REACTION, MachineTests::reactorProcessesReaction);
        GooTestFunctions.registerTest(registrar, MACHINE_REACTOR_REDSTONE, MachineTests::reactorRedstoneHalts);
        GooTestFunctions.registerTest(registrar, MACHINE_PLEXER_RECONSTITUTES,
                MachineTests::plexerReconstitutesItsTargetFromTheGooAbove);
    }


    private static final Logger LOGGER = LogUtils.getLogger();
    private static final BlockPos BE_POS = new BlockPos(1, 1, 1);
    private static final BlockPos INPUT_POS = BE_POS.above();
    private static final int CENTER_SLOT = 4;
    private static final int SETTLE_TICKS = 2;
    private static final int REACTION_TICKS = 5;
    private static final int INPUT_AMOUNT = 1000;
    private static final int TEST_VOLUME = 500;
    /**
     * Corner slot index for second input canister (top-right of 3x3).
     */
    private static final int CORNER_SLOT_2 = 2;

    private static final String SHOULD_INSERT = "Canister should insert into empty slot";
    private static final String GASKET_REGISTERED = "The slotted canister's gasket should be registered before the break";
    private static final String GASKET_RELEASED = "Breaking the block should clear its canister's gasket location";
    private static final String OUTPUT_HANDLER_EXISTS = "Output handler should exist";
    private static final String OUTPUT_IS_REACTION_PRODUCT = "Output should be the reaction's product goo";
    private static final String OUTPUT_AMOUNT_POSITIVE = "Output amount should be positive";
    private static final String REDSTONE_HALTS_OUTPUT = "Output should be empty when redstone halts reactor";
    private static final String FIRST_INPUT_DRAINED = "Reactor should drain the first input canister";
    private static final String SECOND_INPUT_DRAINED = "Reactor should drain the second input canister";
    private static final String FIRST_INPUT_STACK_DRAINED =
            "First input ItemStack should reflect drain (HUD reads from stack)";
    private static final String SECOND_INPUT_STACK_DRAINED =
            "Second input ItemStack should reflect drain (HUD reads from stack)";
    private static final String OUTPUT_STACK_FLUID_PRESENT =
            "Output ItemStack should hold the produced fluid (BER reads from stack)";
    private static final String OUTPUT_STACK_AMOUNT_POSITIVE =
            "Output ItemStack amount should be positive";
    private static final String HANDLER_REBUILT = "Handler should be rebuilt after load";
    private static final String FLUID_SURVIVES = "Fluid type should survive round-trip";
    private static final String AMOUNT_SURVIVES = "Fluid amount should survive round-trip";
    private static final int HUB_SLOT = 0;
    private static final int PLAYER_CANISTER_SLOT = 3;
    private static final int MIXED_BLAZE = 300;
    private static final int MIXED_ROCK = 200;
    private static final int CAPACITY_PROBE = 10_000;
    private static final String MIXED_EXTRACTED = "rock drawn from the mixed canister";
    private static final String MIXED_HANDLER_LEFT = "the slot handler after drawing rock: blaze stands, rock gone";
    private static final String MIXED_STACK_LEFT = "the canister stack after drawing rock: blaze stands, rock gone";
    private static final String MIXED_SCAN_BLAZE = "blaze the scan sees in the mixed canister";
    private static final String MIXED_SCAN_ROCK = "rock the scan sees in the mixed canister";

    private MachineTests() {
    }

    // --- Canister ---

    /**
     * Breaking a canister block clears the registry location of the gasket
     * its slotted canister carries (decision machine-base-owns-the-lifecycle).
     *
     * @param helper the gametest helper
     */
    public static void canisterBreakReleasesSlotGasket(GameTestHelper helper) {
        UUID gasketId = UUID.randomUUID();
        helper.setBlock(BE_POS, GooBlocks.CANISTER.get());
        CanisterBlockEntity be = helper.getBlockEntity(BE_POS, CanisterBlockEntity.class);
        ItemStack canister = new ItemStack(GooItems.CANISTER.get());
        CanisterItem.setMetadata(canister, CanisterItem.getMetadata(canister).withTopGasketId(gasketId));
        be.insertCanister(CENTER_SLOT, canister, false);
        assertBreakClearsGasketLocation(helper, gasketId);
    }

    // --- Reactor ---

    /**
     * Breaking a reactor clears the registry location of the gasket its
     * output canister carries (decision machine-base-owns-the-lifecycle).
     *
     * @param helper the gametest helper
     */
    public static void reactorBreakReleasesOutputGasket(GameTestHelper helper) {
        UUID gasketId = UUID.randomUUID();
        helper.setBlock(BE_POS, GooBlocks.REACTOR.get());
        ReactorBlockEntity reactor = helper.getBlockEntity(BE_POS, ReactorBlockEntity.class);
        ItemStack output = new ItemStack(GooItems.CANISTER.get());
        CanisterItem.setMetadata(output, CanisterItem.getMetadata(output).withBottomGasketId(gasketId));
        reactor.insertOutputCanister(output);
        assertBreakClearsGasketLocation(helper, gasketId);
    }

    /**
     * Asserts the gasket is registered, breaks the block at the test's
     * position, and asserts the gasket's registry location is cleared.
     *
     * @param helper   the gametest helper
     * @param gasketId the gasket the broken block's canister carries
     */
    private static void assertBreakClearsGasketLocation(GameTestHelper helper, UUID gasketId) {
        GasketRegistry registry = GasketRegistry.get(helper.getLevel());
        helper.assertTrue(registry.getLocation(gasketId) != null, GASKET_REGISTERED);
        helper.runAfterDelay(1, () -> {
            helper.destroyBlock(BE_POS);
            helper.runAfterDelay(SETTLE_TICKS, () -> {
                helper.assertTrue(registry.getLocation(gasketId) == null, GASKET_RELEASED);
                helper.succeed();
            });
        });
    }

    /**
     * A reactor with valid inputs should consume them and produce output.
     * Uses the blaze_from_blaze_leaf reaction: 1 blaze + 1 leaf -> 2 blaze.
     *
     * @param helper the gametest helper
     */
    public static void reactorProcessesReaction(GameTestHelper helper) {
        assertReactorMakes(helper, GooFluids.resource(GooTypes.BLAZE),
                GooFluids.resource(GooTypes.LEAF), GooFluids.resource(GooTypes.BLAZE));
    }

    /**
     * Feeds a reactor two inputs and, after the reaction window, asserts the
     * output canister holds the expected goo and both inputs drained.
     *
     * @param helper      the gametest helper
     * @param firstInput  the goo in the first input canister
     * @param secondInput the goo in the second input canister
     * @param output      the goo the reaction should produce
     */
    private static void assertReactorMakes(GameTestHelper helper,
            FluidResource firstInput, FluidResource secondInput, FluidResource output) {
        ReactorBlockEntity reactor = placeFedReactor(helper, firstInput, secondInput);
        helper.runAfterDelay(REACTION_TICKS, () -> assertReactionConsumed(helper, reactor,
                helper.getBlockEntity(INPUT_POS, CanisterBlockEntity.class), output));
    }

    /**
     * Places a reactor with an empty output canister under a canister block
     * holding the blaze and leaf inputs of the blaze_from_blaze_leaf reaction.
     *
     * @param helper the gametest helper
     * @return the reactor block entity
     */
    private static ReactorBlockEntity placeFedReactor(GameTestHelper helper) {
        return placeFedReactor(helper, GooFluids.resource(GooTypes.BLAZE), GooFluids.resource(GooTypes.LEAF));
    }

    /**
     * Places a reactor with an empty output canister under a canister block
     * holding the two inputs.
     *
     * @param helper      the gametest helper
     * @param firstInput  the goo filling the first input canister
     * @param secondInput the goo filling the second input canister
     * @return the reactor block entity
     */
    private static ReactorBlockEntity placeFedReactor(GameTestHelper helper,
            FluidResource firstInput, FluidResource secondInput) {
        helper.setBlock(BE_POS, GooBlocks.REACTOR.get());
        ReactorBlockEntity reactor = helper.getBlockEntity(BE_POS, ReactorBlockEntity.class);
        helper.setBlock(INPUT_POS, GooBlocks.CANISTER.get());
        CanisterBlockEntity inputBe = helper.getBlockEntity(INPUT_POS, CanisterBlockEntity.class);
        insertFilledCanister(inputBe, 0, firstInput, INPUT_AMOUNT);
        insertFilledCanister(inputBe, CORNER_SLOT_2, secondInput, INPUT_AMOUNT);
        reactor.insertOutputCanister(new ItemStack(GooItems.CANISTER.get()));
        return reactor;
    }

    /**
     * Verifies output present, both inputs drained on the handler, and both
     * input ItemStacks reflect the drain (the HUD/billboard reads stack
     * NBT, not the handler).
     *
     * @param helper  the gametest helper
     * @param reactor the reactor block entity
     * @param inputBe the input canister block entity above
     * @param output  the expected output fluid
     */
    private static void assertReactionConsumed(GameTestHelper helper,
            ReactorBlockEntity reactor, CanisterBlockEntity inputBe, FluidResource output) {
        assertOutputHandler(helper, reactor, output);
        assertInputsDrained(helper, inputBe);
        assertOutputStackHasFluid(helper, reactor, output);
        helper.succeed();
    }

    /**
     * @param helper  the gametest helper
     * @param reactor the reactor block entity
     * @param output  the expected fluid
     */
    private static void assertOutputHandler(GameTestHelper helper,
            ReactorBlockEntity reactor, FluidResource output) {
        var handler = reactor.containerState().getSlotFluidHandler(
                ReactorBlockEntity.OUTPUT_SLOT);
        helper.assertTrue(handler != null, OUTPUT_HANDLER_EXISTS);
        helper.assertTrue(handler.toFluidContent().dominantResource().equals(output), OUTPUT_IS_REACTION_PRODUCT);
        helper.assertTrue(handler.totalVolume() > 0, OUTPUT_AMOUNT_POSITIVE);
    }

    /**
     * @param helper  the gametest helper
     * @param inputBe the input canister BE above the reactor
     */
    private static void assertInputsDrained(GameTestHelper helper,
            CanisterBlockEntity inputBe) {
        var firstHandler = inputBe.containerState().getSlotFluidHandler(0);
        var secondHandler = inputBe.containerState().getSlotFluidHandler(CORNER_SLOT_2);
        helper.assertTrue(firstHandler != null && firstHandler.totalVolume() < INPUT_AMOUNT,
                FIRST_INPUT_DRAINED);
        helper.assertTrue(secondHandler != null && secondHandler.totalVolume() < INPUT_AMOUNT,
                SECOND_INPUT_DRAINED);
        CanisterFluidContent firstStackContent = CanisterItem.getFluidContent(
                inputBe.containerState().getCanister(0));
        CanisterFluidContent secondStackContent = CanisterItem.getFluidContent(
                inputBe.containerState().getCanister(CORNER_SLOT_2));
        helper.assertTrue(firstStackContent.totalVolume() < INPUT_AMOUNT,
                FIRST_INPUT_STACK_DRAINED);
        helper.assertTrue(secondStackContent.totalVolume() < INPUT_AMOUNT,
                SECOND_INPUT_STACK_DRAINED);
    }

    /**
     * @param helper  the gametest helper
     * @param reactor the reactor block entity
     * @param output  the expected fluid in the output stack
     */
    private static void assertOutputStackHasFluid(GameTestHelper helper,
            ReactorBlockEntity reactor, FluidResource output) {
        CanisterFluidContent outputStackContent = CanisterItem.getFluidContent(
                reactor.getOutputCanister());
        helper.assertTrue(outputStackContent.dominantResource().equals(output),
                OUTPUT_STACK_FLUID_PRESENT);
        helper.assertTrue(outputStackContent.totalVolume() > 0,
                OUTPUT_STACK_AMOUNT_POSITIVE);
    }

    /**
     * A reactor receiving redstone should not process reactions even with
     * valid inputs.
     *
     * @param helper the gametest helper
     */
    public static void reactorRedstoneHalts(GameTestHelper helper) {
        ReactorBlockEntity reactor = placeFedReactor(helper);
        helper.setBlock(BE_POS.east(), Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(REACTION_TICKS, () -> {
            assertOutputEmpty(helper, reactor, REDSTONE_HALTS_OUTPUT);
            helper.succeed();
        });
    }

    /**
     * Asserts the reactor's output canister holds no goo.
     *
     * @param helper  the gametest helper
     * @param reactor the reactor block entity
     * @param message the failure message
     */
    private static void assertOutputEmpty(GameTestHelper helper, ReactorBlockEntity reactor, String message) {
        var handler = reactor.containerState().getSlotFluidHandler(ReactorBlockEntity.OUTPUT_SLOT);
        helper.assertTrue(handler != null, OUTPUT_HANDLER_EXISTS);
        helper.assertTrue(handler.totalVolume() == 0, message);
    }

    // --- Canister fluid operations ---

    /**
     * A canister block with fluid should survive a save/load round-trip.
     * Exercises CanisterEntitySerializer.saveCanisterList, loadCanisterList,
     * saveStreamState, loadStreamState, and handler rebuild.
     *
     * @param helper the gametest helper
     */
    public static void canisterSurvivesRoundTrip(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.CANISTER.get());
        CanisterBlockEntity be = helper.getBlockEntity(BE_POS, CanisterBlockEntity.class);

        FluidResource blazeFluid = GooFluids.resource(GooTypes.BLAZE);
        insertFilledCanister(be, CENTER_SLOT, blazeFluid, TEST_VOLUME);

        // Snapshot the BE state as NBT
        CompoundTag saved = be.getUpdateTag(helper.getLevel().registryAccess());

        // Destroy and re-place the block to get a fresh BE
        helper.destroyBlock(BE_POS);
        helper.setBlock(BE_POS, GooBlocks.CANISTER.get());
        CanisterBlockEntity restored = helper.getBlockEntity(BE_POS, CanisterBlockEntity.class);

        // Load the saved state into the fresh BE
        try (var reporter = new ProblemReporter.ScopedCollector(
                restored.problemPath(), LOGGER)) {
            restored.loadCustomOnly(TagValueInput.create(
                    reporter, helper.getLevel().registryAccess(), saved));
        }

        // Verify the fluid survived the round-trip
        var handler = restored.containerState().getSlotFluidHandler(CENTER_SLOT);
        helper.assertTrue(handler != null, HANDLER_REBUILT);
        helper.assertTrue(handler.toFluidContent().dominantResource().equals(blazeFluid), FLUID_SURVIVES);
        helper.assertTrue(handler.totalVolume() == TEST_VOLUME, AMOUNT_SURVIVES);
        helper.succeed();
    }

    // --- Mixed canisters (decision canisters-hold-more-than-one-goo-type) ---

    /**
     * A hub extracting one goo type from a canister holding two takes that type
     * alone: the slot's handler and the canister stack both keep the other type.
     *
     * @param helper the gametest helper
     */
    public static void hubExtractsOneTypeFromAMixedCanister(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.HUB.get());
        HubBlockEntity hub = helper.getBlockEntity(BE_POS, HubBlockEntity.class);
        helper.assertTrue(hub.insertCanister(HUB_SLOT, mixedCanister()), SHOULD_INSERT);

        int extracted;
        try (var tx = Transaction.openRoot()) {
            extracted = hub.getFluidHandler().extract(HUB_SLOT, GooFluids.resource(GooTypes.ROCK), CAPACITY_PROBE, tx);
            tx.commit();
        }

        helper.assertValueEqual(extracted, MIXED_ROCK, MIXED_EXTRACTED);
        assertMixedLeftBlaze(helper, hub.getSlotFluidContent(HUB_SLOT), MIXED_HANDLER_LEFT);
        assertMixedLeftBlaze(helper, CanisterItem.getFluidContent(hub.getCanister(HUB_SLOT)), MIXED_STACK_LEFT);
        helper.succeed();
    }

    /**
     * A player carrying a canister holding two goo types holds a source of each, and
     * spending one through GooSourceScanner draws that type alone from the canister.
     *
     * @param helper the gametest helper
     */
    public static void mixedCanisterIsAGooSourceForEachType(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getInventory().setItem(PLAYER_CANISTER_SLOT, mixedCanister());

        var available = GooSourceScanner.aggregateAvailable(player);
        helper.assertValueEqual(available.getOrDefault(GooTypes.BLAZE, 0), MIXED_BLAZE, MIXED_SCAN_BLAZE);
        helper.assertValueEqual(available.getOrDefault(GooTypes.ROCK, 0), MIXED_ROCK, MIXED_SCAN_ROCK);

        helper.assertValueEqual(GooSourceScanner.deplete(player, GooTypes.ROCK, MIXED_ROCK), MIXED_ROCK,
                MIXED_EXTRACTED);
        assertMixedLeftBlaze(helper, CanisterItem.getFluidContent(player.getInventory().getItem(PLAYER_CANISTER_SLOT)),
                MIXED_STACK_LEFT);
        helper.succeed();
    }

    private static ItemStack mixedCanister() {
        ItemStack canister = new ItemStack(GooItems.CANISTER.get());
        CanisterItem.addGoo(canister, GooTypes.BLAZE, MIXED_BLAZE);
        CanisterItem.addGoo(canister, GooTypes.ROCK, MIXED_ROCK);
        return canister;
    }

    private static void assertMixedLeftBlaze(GameTestHelper helper, CanisterFluidContent content, String where) {
        helper.assertValueEqual(content.volumeOf(GooTypes.BLAZE), MIXED_BLAZE, where);
        helper.assertValueEqual(content.volumeOf(GooTypes.ROCK), 0, where);
    }

    // --- Plexer ---

    /**
     * Creates a canister item with the given fluid content and inserts it
     * into a slot on a canister block entity.
     *
     * @param be     the canister block entity
     * @param slot   the slot index
     * @param fluid  the fluid resource to fill with
     * @param amount the amount in mB
     */
    private static void insertFilledCanister(CanisterBlockEntity be,
                                             int slot, FluidResource fluid, int amount) {
        ItemStack canister = new ItemStack(GooItems.CANISTER.get());
        CanisterItem.setFluidContent(canister, CanisterFluidContent.of(fluid, amount));
        be.insertCanister(slot, canister, false);
    }

    /**
     * A plexer targeting stone, a canister of each of stone's goo types standing above it, reconstitutes a stone
     * from that goo.
     *
     * @param helper the gametest helper
     */
    public static void plexerReconstitutesItsTargetFromTheGooAbove(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.PLEXER.get());
        helper.setBlock(BE_POS.above(), GooBlocks.CANISTER.get());
        PlexerBlockEntity plexer = helper.getBlockEntity(BE_POS, PlexerBlockEntity.class);
        CanisterBlockEntity above = helper.getBlockEntity(BE_POS.above(), CanisterBlockEntity.class);
        ItemStack stone = new ItemStack(Items.STONE);
        GooValue value = GooValues.of(helper.getLevel()).lookup(stone);
        helper.assertTrue(value != null && !value.isEmpty(), STONE_HAS_VALUE);
        int slot = 0;
        for (ResourceKey<GooTypeDefinition> type : GooTypes.order()) {
            int needed = value.fabricateCeiling(type);
            if (needed > 0) {
                ItemStack canister = new ItemStack(GooItems.CANISTER.get());
                CanisterItem.addGoo(canister, type, needed);
                above.insertCanister(slot++, canister, false);
            }
        }
        plexer.setTargetItem(stone);

        ItemStack made = plexer.tryReconstitute();

        helper.assertTrue(made.is(Items.STONE), PLEXER_MADE_NOTHING);
        helper.succeed();
    }
}