package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.block.canister.CanisterBlock;
import com.mercuriusxeno.goo.block.canister.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.canister.CanisterSlotFluidHandler;
import com.mercuriusxeno.goo.block.crucible.CrucibleBlock;
import com.mercuriusxeno.goo.block.crucible.CrucibleBlockEntity;
import com.mercuriusxeno.goo.block.reactor.ReactorBlockEntity;
import com.mercuriusxeno.goo.data.GasketRegistry;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.gasket.GasketPartner;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooFluids;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import java.util.UUID;

/**
 * Gametests for {@link com.mercuriusxeno.goo.block.gasket.GasketPusher} tick guard
 * and dispose behavior on real block entities, and for the reactor output
 * canister pushing through the same pipeline as a canister block slot.
 */
public final class GasketPusherTests {

    private static final BlockPos BE_POS = new BlockPos(1, 1, 1);
    private static final int IDLE_TICKS = 25;
    private static final int SETTLE_TICKS = 2;
    private static final String EMPTY_AFTER_IDLE = "Crucible reservoir should still be empty after idle ticks";
    private static final String NO_CRASH_NO_PARTNER = "Crucible should not crash or produce goo without a partner";

    // --- Reactor fixtures (reactor-output-push-fix) ---

    /** Beside the reactor, not above it, so the reactor reads it as a receiver and not an input. */
    private static final BlockPos RECEIVER_POS = new BlockPos(3, 1, 1);
    private static final int OUTPUT_GOO = 1000;
    /** Ticks for the pusher to move goo; it pushes a tapered volume every tick. */
    private static final int PUSH_TICKS = 5;
    private static final String RECEIVER_ROSE = "Receiver canister should hold goo after the reactor pushed";
    private static final String VOLUME_CONSERVED =
            "Goo the reactor output lost should equal goo the receiver gained";
    private static final String RECEIVER_UNCHANGED_AFTER_REMOVAL =
            "Receiver goo should not change after the output canister is removed";

    private GasketPusherTests() {}

    /**
     * An empty crucible (no items, no goo) should tick its pusher without
     * syncing or crashing. Exercises the tick guard's "source empty" path.
     *
     * @param helper the gametest helper
     */
    public static void emptyReservoirSkipsTick(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.CRUCIBLE.get().defaultBlockState()
            .setValue(CrucibleBlock.HAS_GASKET, true));
        helper.runAfterDelay(IDLE_TICKS, () -> {
            CrucibleBlockEntity be = helper.getBlockEntity(BE_POS, CrucibleBlockEntity.class);
            helper.assertTrue(be.reservoirHandler().isEmpty(), EMPTY_AFTER_IDLE);
            helper.succeed();
        });
    }

    /**
     * A crucible with a gasket but no partner should tick safely.
     * Exercises the "null partner" guard path.
     *
     * @param helper the gametest helper
     */
    public static void noPartnerSkipsTick(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.CRUCIBLE.get().defaultBlockState()
            .setValue(CrucibleBlock.HAS_GASKET, true));
        CrucibleBlockEntity be = helper.getBlockEntity(BE_POS, CrucibleBlockEntity.class);
        be.gasketState().ensureId(GasketRole.TRANSMITTER, () -> {});
        helper.runAfterDelay(IDLE_TICKS, () -> {
            helper.assertTrue(be.reservoirHandler().isEmpty(), NO_CRASH_NO_PARTNER);
            helper.succeed();
        });
    }

    /**
     * Breaking a crucible that has a gasket and then ticking the world should
     * not throw. Exercises the real-world dispose path (setRemoved lifecycle).
     *
     * @param helper the gametest helper
     */
    public static void disposeAndTickIsSafe(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.CRUCIBLE.get().defaultBlockState()
            .setValue(CrucibleBlock.HAS_GASKET, true));
        helper.runAfterDelay(1, () -> {
            helper.destroyBlock(BE_POS);
            helper.runAfterDelay(SETTLE_TICKS, helper::succeed);
        });
    }

    /**
     * Breaking a crucible, re-placing it, and breaking it again should not throw.
     * Double-lifecycle on the same position.
     *
     * @param helper the gametest helper
     */
    public static void doubleDisposeIsSafe(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.CRUCIBLE.get().defaultBlockState()
            .setValue(CrucibleBlock.HAS_GASKET, true));
        helper.runAfterDelay(1, () -> {
            helper.destroyBlock(BE_POS);
            helper.runAfterDelay(1, () -> {
                helper.setBlock(BE_POS, GooBlocks.CRUCIBLE.get().defaultBlockState()
                    .setValue(CrucibleBlock.HAS_GASKET, true));
                helper.runAfterDelay(1, () -> {
                    helper.destroyBlock(BE_POS);
                    helper.runAfterDelay(SETTLE_TICKS, helper::succeed);
                });
            });
        });
    }

    // --- Reactor (reactor-output-push-fix) ---

    /**
     * Reactor: an output canister holding goo, bottom-gasketed and linked to a
     * canister block receiver, pushes goo to the receiver, and the volume the
     * output lost equals the volume the receiver gained.
     *
     * @param helper the gametest helper
     */
    public static void reactorOutputPushesToLinkedReceiver(GameTestHelper helper) {
        ReactorBlockEntity reactor = placeLinkedReactor(helper);
        CanisterBlockEntity receiver = helper.getBlockEntity(RECEIVER_POS, CanisterBlockEntity.class);
        helper.runAfterDelay(PUSH_TICKS, () -> {
            int received = receiverAmount(receiver);
            int remaining = outputAmount(reactor);
            helper.assertTrue(received > 0, RECEIVER_ROSE);
            helper.assertTrue(OUTPUT_GOO - remaining == received, VOLUME_CONSERVED);
            helper.succeed();
        });
    }

    /**
     * Reactor: removing the linked output canister disposes its pusher, so the
     * receiver's goo stays where it was through the ticks that follow.
     *
     * @param helper the gametest helper
     */
    public static void reactorOutputRemovalStopsPush(GameTestHelper helper) {
        ReactorBlockEntity reactor = placeLinkedReactor(helper);
        CanisterBlockEntity receiver = helper.getBlockEntity(RECEIVER_POS, CanisterBlockEntity.class);
        helper.runAfterDelay(PUSH_TICKS, () -> {
            reactor.removeOutputCanister();
            int receivedAtRemoval = receiverAmount(receiver);
            helper.runAfterDelay(PUSH_TICKS, () -> {
                helper.assertTrue(receiverAmount(receiver) == receivedAtRemoval,
                        RECEIVER_UNCHANGED_AFTER_REMOVAL);
                helper.succeed();
            });
        });
    }

    // --- Reactor helpers ---

    /**
     * Places a reactor whose goo-filled output canister carries a bottom gasket,
     * a canister block whose center canister carries a top gasket, and links the
     * two through the registry and both holders' partner metadata, the writes
     * the choral tuner makes on a completed link.
     *
     * @param helper the gametest helper
     * @return the reactor block entity
     */
    private static ReactorBlockEntity placeLinkedReactor(GameTestHelper helper) {
        UUID outputGasket = UUID.randomUUID();
        UUID receiverGasket = UUID.randomUUID();
        helper.setBlock(BE_POS, GooBlocks.REACTOR.get());
        ReactorBlockEntity reactor = helper.getBlockEntity(BE_POS, ReactorBlockEntity.class);
        ItemStack output = new ItemStack(GooItems.CANISTER.get());
        CanisterItem.setFluidContent(output,
                new CanisterFluidContent(GooFluids.resource(GooTypes.BLAZE), OUTPUT_GOO));
        CanisterItem.setMetadata(output, CanisterItem.getMetadata(output).withBottomGasketId(outputGasket));
        reactor.insertOutputCanister(output);

        helper.setBlock(RECEIVER_POS, GooBlocks.CANISTER.get());
        CanisterBlockEntity receiver = helper.getBlockEntity(RECEIVER_POS, CanisterBlockEntity.class);
        ItemStack receiving = new ItemStack(GooItems.CANISTER.get());
        CanisterItem.setMetadata(receiving, CanisterItem.getMetadata(receiving).withTopGasketId(receiverGasket));
        receiver.insertCanister(CanisterBlock.CENTER_SLOT, receiving, false);

        GasketRegistry.get(helper.getLevel()).link(outputGasket, receiverGasket);
        receiver.setPartner(GasketRole.RECEIVER, CanisterBlock.CENTER_SLOT,
                new GasketPartner(helper.absolutePos(BE_POS), ReactorBlockEntity.OUTPUT_SLOT));
        reactor.setPartner(GasketRole.TRANSMITTER, ReactorBlockEntity.OUTPUT_SLOT,
                new GasketPartner(helper.absolutePos(RECEIVER_POS), CanisterBlock.CENTER_SLOT));
        return reactor;
    }

    private static int outputAmount(ReactorBlockEntity reactor) {
        CanisterSlotFluidHandler handler = reactor.containerState().getSlotFluidHandler(ReactorBlockEntity.OUTPUT_SLOT);
        return handler == null ? 0 : handler.getAmount();
    }

    private static int receiverAmount(CanisterBlockEntity receiver) {
        CanisterSlotFluidHandler handler = receiver.containerState().getSlotFluidHandler(CanisterBlock.CENTER_SLOT);
        return handler == null ? 0 : handler.getAmount();
    }
}
