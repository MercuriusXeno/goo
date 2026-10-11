package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.block.canister.CanisterBlock;
import com.mercuriusxeno.goo.block.canister.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.canister.CanisterSlotFluidHandler;
import com.mercuriusxeno.goo.block.crucible.CrucibleBlock;
import com.mercuriusxeno.goo.block.crucible.CrucibleBlockEntity;
import com.mercuriusxeno.goo.block.fluid.GooFluidHandler;
import com.mercuriusxeno.goo.block.gasket.ChoralGasketBlock;
import com.mercuriusxeno.goo.block.gasket.ChoralGasketBlockEntity;
import com.mercuriusxeno.goo.block.reactor.ReactorBlockEntity;
import com.mercuriusxeno.goo.block.vat.VatBlock;
import com.mercuriusxeno.goo.block.vat.VatBlockEntity;
import com.mercuriusxeno.goo.data.GasketRegistry;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.gasket.GasketPartner;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooFluids;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.registry.GooTickets;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
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
    /** Blocks around the broken crucible searched for its dropped gasket. */
    private static final double DROP_RANGE = 1.5;
    private static final String RECEIVER_NEVER_ROSE = "Receiver should hold goo before the output canister is removed";
    private static final String EMPTY_AFTER_IDLE = "Crucible reservoir should still be empty after idle ticks";
    private static final String NO_CRASH_NO_PARTNER = "Crucible should not crash or produce goo without a partner";

    // --- Pusher release fixtures (diagnose-then-fix-capability-lifetimes) ---

    private static final int CHUNK_WIDTH = 16;
    /** Lifts the partner canister clear of neighbouring test bays in the next chunk. */
    private static final int PARTNER_RISE = 24;
    private static final String PARTNER_CHUNK_NEVER_FORCED =
            "Crucible should hold a ticket on its partner's chunk once linked";
    private static final String PARTNER_CHUNK_STILL_FORCED =
            "Crucible should hold no ticket on its partner's chunk once it left";

    /** The host the linked canister is carried to. */
    private static final BlockPos SECOND_HOST_POS = new BlockPos(3, 1, 3);
    private static final String MOVED_RECEIVER_ROSE =
            "Canister carried to a second host should keep receiving goo with no re-link";
    private static final String FIRST_HOST_STAYS_EMPTY = "First host's emptied slot should receive no goo";

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

    // --- Waterlogged choral gasket fixtures (diagnose-then-fix-waterlogged-gasket-link) ---

    /** Above the sturdy block at BE_POS, which the gasket block needs to survive. */
    private static final BlockPos GASKET_POS = new BlockPos(1, 2, 1);
    /** Inside the gasket block's one-pixel-tall shape, in its lower half: the transmitter. */
    private static final double GASKET_HIT_Y = 0.5 / 16.0;
    private static final double HALF = 0.5;
    private static final String VAT_WATER_ROSE = "Vat should hold water after the waterlogged gasket pushed";
    private static final String VAT_WATER_KEEPS_RISING = "Vat water should keep rising while the gasket pushes";
    private static final String GASKET_STAYS_WATERLOGGED = "Gasket block should stay waterlogged while it pushes";
    private static final String READ = ", read ";
    private static final String THEN = " then ";

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
     * Breaking a gasketed crucible drops its gasket once through the
     * setRemoved lifecycle, and the ticks after the break leave that one drop.
     *
     * @param helper the gametest helper
     */
    public static void disposeDropsGasketOnce(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.CRUCIBLE.get().defaultBlockState()
            .setValue(CrucibleBlock.HAS_GASKET, true));
        helper.runAfterDelay(1, () -> {
            helper.destroyBlock(BE_POS);
            helper.runAfterDelay(SETTLE_TICKS, () -> {
                helper.assertItemEntityCountIs(GooItems.CHORAL_GASKET.get(), BE_POS, DROP_RANGE, 1);
                helper.succeed();
            });
        });
    }

    /**
     * Breaking a gasketed crucible, re-placing it and breaking it again drops
     * one gasket per lifecycle on the same position.
     *
     * @param helper the gametest helper
     */
    public static void doubleDisposeDropsGasketEachTime(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.CRUCIBLE.get().defaultBlockState()
            .setValue(CrucibleBlock.HAS_GASKET, true));
        helper.runAfterDelay(1, () -> {
            helper.destroyBlock(BE_POS);
            helper.runAfterDelay(1, () -> {
                helper.setBlock(BE_POS, GooBlocks.CRUCIBLE.get().defaultBlockState()
                    .setValue(CrucibleBlock.HAS_GASKET, true));
                helper.runAfterDelay(1, () -> {
                    helper.destroyBlock(BE_POS);
                    helper.runAfterDelay(SETTLE_TICKS, () -> {
                        helper.assertItemEntityCountIs(GooItems.CHORAL_GASKET.get(), BE_POS, DROP_RANGE, 2);
                        helper.succeed();
                    });
                });
            });
        });
    }

    // --- Pusher release (diagnose-then-fix-capability-lifetimes) ---

    /**
     * Breaking a crucible whose transmitter is linked to a canister in another chunk
     * leaves no gasket chunk ticket owned by the crucible's position.
     *
     * @param helper the gametest helper
     */
    public static void crucibleBreakReleasesPartnerChunk(GameTestHelper helper) {
        BlockPos partner = placeCrucibleLinkedAcrossChunks(helper);
        helper.runAfterDelay(1, () -> {
            helper.assertTrue(cruciblePartnerChunkHeld(helper, partner), PARTNER_CHUNK_NEVER_FORCED);
            helper.destroyBlock(BE_POS);
            helper.runAfterDelay(1, () -> {
                helper.assertFalse(cruciblePartnerChunkHeld(helper, partner), PARTNER_CHUNK_STILL_FORCED);
                helper.getLevel().removeBlock(partner, false);
                helper.succeed();
            });
        });
    }

    /**
     * A linked crucible whose chunk unloads, through NeoForge's onChunkUnloaded then
     * setRemoved, leaves no gasket chunk ticket owned by the crucible's position.
     *
     * @param helper the gametest helper
     */
    public static void crucibleUnloadReleasesPartnerChunk(GameTestHelper helper) {
        BlockPos partner = placeCrucibleLinkedAcrossChunks(helper);
        helper.runAfterDelay(1, () -> {
            helper.assertTrue(cruciblePartnerChunkHeld(helper, partner), PARTNER_CHUNK_NEVER_FORCED);
            CrucibleBlockEntity crucible = helper.getBlockEntity(BE_POS, CrucibleBlockEntity.class);
            crucible.onChunkUnloaded();
            crucible.setRemoved();
            helper.assertFalse(cruciblePartnerChunkHeld(helper, partner), PARTNER_CHUNK_STILL_FORCED);
            helper.getLevel().removeBlock(partner, false);
            helper.succeed();
        });
    }

    /**
     * Places a gasketed crucible and, in the next chunk east and above the test bay,
     * a canister block whose center canister carries a top gasket, then links the
     * crucible's transmitter to it the way the choral tuner does.
     *
     * @param helper the gametest helper
     * @return the canister block's absolute position
     */
    private static BlockPos placeCrucibleLinkedAcrossChunks(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.CRUCIBLE.get().defaultBlockState()
                .setValue(CrucibleBlock.HAS_GASKET, true));
        CrucibleBlockEntity crucible = helper.getBlockEntity(BE_POS, CrucibleBlockEntity.class);
        UUID transmitter = crucible.ensureGasketId(GasketRole.TRANSMITTER);

        BlockPos crucibleAbs = helper.absolutePos(BE_POS);
        int nextChunkX = (ChunkPos.containing(crucibleAbs).x() + 1) * CHUNK_WIDTH + CHUNK_WIDTH / 2;
        BlockPos partner = new BlockPos(nextChunkX, crucibleAbs.getY() + PARTNER_RISE, crucibleAbs.getZ());
        helper.getLevel().setBlockAndUpdate(partner, GooBlocks.CANISTER.get().defaultBlockState());
        CanisterBlockEntity canister = (CanisterBlockEntity) helper.getLevel().getBlockEntity(partner);
        UUID receiver = UUID.randomUUID();
        ItemStack receiving = new ItemStack(GooItems.CANISTER.get());
        CanisterItem.setMetadata(receiving, CanisterItem.getMetadata(receiving).withTopGasketId(receiver));
        canister.insertCanister(CanisterBlock.CENTER_SLOT, receiving, false);

        GasketRegistry.get(helper.getLevel()).link(transmitter, receiver);
        crucible.setPartner(GasketRole.TRANSMITTER, new GasketPartner(partner, CanisterBlock.CENTER_SLOT));
        return partner;
    }

    /**
     * Whether the crucible's position owns a gasket ticket on the partner's chunk.
     * NeoForge's forceChunk answers whether it changed anything, so adding a ticket
     * already held answers false; the probe then leaves a held ticket as it was.
     *
     * @param helper  the gametest helper
     * @param partner the partner's absolute position
     * @return true if the ticket is held
     */
    private static boolean cruciblePartnerChunkHeld(GameTestHelper helper, BlockPos partner) {
        ChunkPos chunk = ChunkPos.containing(partner);
        BlockPos owner = helper.absolutePos(BE_POS);
        boolean added = GooTickets.gasketChunks.forceChunk(
                helper.getLevel(), owner, chunk.x(), chunk.z(), true, false);
        if (added) {
            GooTickets.gasketChunks.forceChunk(helper.getLevel(), owner, chunk.x(), chunk.z(), false, false);
        }
        return !added;
    }

    /**
     * A crucible linked to a canister's receiver gasket keeps pushing once the canister is
     * carried from its first host into a second, with no re-link: the moved canister gains
     * goo in the second host and the first host's emptied slot gains none.
     *
     * @param helper the gametest helper
     */
    public static void movedReceiverKeepsReceiving(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.CRUCIBLE.get().defaultBlockState()
                .setValue(CrucibleBlock.HAS_GASKET, true));
        CrucibleBlockEntity crucible = helper.getBlockEntity(BE_POS, CrucibleBlockEntity.class);
        UUID transmitter = crucible.ensureGasketId(GasketRole.TRANSMITTER);
        crucible.insertGoo(GooTypes.BLAZE, OUTPUT_GOO);
        helper.setBlock(RECEIVER_POS, GooBlocks.CANISTER.get());
        helper.setBlock(SECOND_HOST_POS, GooBlocks.CANISTER.get());
        CanisterBlockEntity first = helper.getBlockEntity(RECEIVER_POS, CanisterBlockEntity.class);
        CanisterBlockEntity second = helper.getBlockEntity(SECOND_HOST_POS, CanisterBlockEntity.class);
        UUID receiver = UUID.randomUUID();
        ItemStack receiving = new ItemStack(GooItems.CANISTER.get());
        CanisterItem.setMetadata(receiving, CanisterItem.getMetadata(receiving).withTopGasketId(receiver));
        first.insertCanister(CanisterBlock.CENTER_SLOT, receiving, false);
        GasketRegistry.get(helper.getLevel()).link(transmitter, receiver);
        crucible.setPartner(GasketRole.TRANSMITTER,
                new GasketPartner(helper.absolutePos(RECEIVER_POS), CanisterBlock.CENTER_SLOT));

        helper.runAfterDelay(PUSH_TICKS, () -> {
            helper.assertTrue(receiverAmount(first) > 0, RECEIVER_ROSE);
            ItemStack carried = first.removeCanister(CanisterBlock.CENTER_SLOT);
            second.insertCanister(CanisterBlock.CENTER_SLOT, carried, false);
            int carriedIn = receiverAmount(second);
            crucible.insertGoo(GooTypes.BLAZE, OUTPUT_GOO);
            helper.runAfterDelay(PUSH_TICKS, () -> {
                int now = receiverAmount(second);
                helper.assertTrue(now > carriedIn, MOVED_RECEIVER_ROSE + READ + carriedIn + THEN + now);
                helper.assertTrue(receiverAmount(first) == 0, FIRST_HOST_STAYS_EMPTY);
                helper.succeed();
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
            helper.assertTrue(receivedAtRemoval > 0, RECEIVER_NEVER_ROSE);
            helper.runAfterDelay(PUSH_TICKS, () -> {
                helper.assertTrue(receiverAmount(receiver) == receivedAtRemoval,
                        RECEIVER_UNCHANGED_AFTER_REMOVAL);
                helper.succeed();
            });
        });
    }

    // --- Waterlogged choral gasket (diagnose-then-fix-waterlogged-gasket-link) ---

    /**
     * A waterlogged choral gasket block whose transmitter is linked on the choral
     * tuner to a vat's receiver cap pushes water into the vat, and keeps pushing:
     * the vat's water rises between two reads and the gasket stays waterlogged.
     *
     * @param helper the gametest helper
     */
    public static void waterloggedGasketPushesIntoVat(GameTestHelper helper) {
        VatBlockEntity vat = placeTunerLinkedGasketAndVat(helper);
        helper.runAfterDelay(PUSH_TICKS, () -> {
            long earlier = vatWater(vat);
            helper.assertTrue(earlier > 0, VAT_WATER_ROSE + READ + earlier);
            helper.runAfterDelay(PUSH_TICKS, () -> {
                long later = vatWater(vat);
                helper.assertTrue(later > earlier, VAT_WATER_KEEPS_RISING + READ + earlier + THEN + later);
                helper.assertTrue(helper.getBlockState(GASKET_POS).getValue(ChoralGasketBlock.WATERLOGGED),
                        GASKET_STAYS_WATERLOGGED);
                helper.succeed();
            });
        });
    }

    /**
     * Sets a sturdy block, a waterlogged choral gasket block on it and a capped
     * vat beside it, then links the gasket's transmitter to the vat's cap on the
     * choral tuner: gasket block first, vat cap second.
     *
     * @param helper the gametest helper
     * @return the vat block entity
     */
    private static VatBlockEntity placeTunerLinkedGasketAndVat(GameTestHelper helper) {
        helper.setBlock(BE_POS, Blocks.STONE);
        helper.setBlock(GASKET_POS, GooBlocks.CHORAL_GASKET_BLOCK.get().defaultBlockState()
                .setValue(ChoralGasketBlock.WATERLOGGED, true));
        helper.getBlockEntity(GASKET_POS, ChoralGasketBlockEntity.class).ensureGasketId(GasketRole.TRANSMITTER);
        helper.setBlock(RECEIVER_POS, GooBlocks.VAT.get().defaultBlockState().setValue(VatBlock.GASKET_CAP, true));
        VatBlockEntity vat = helper.getBlockEntity(RECEIVER_POS, VatBlockEntity.class);
        vat.ensureGasketId(GasketRole.RECEIVER);

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.CHORAL_TUNER.get()));
        BlockPos gasketAbs = helper.absolutePos(GASKET_POS);
        helper.useBlock(GASKET_POS, player, new BlockHitResult(
                new Vec3(gasketAbs.getX() + HALF, gasketAbs.getY() + GASKET_HIT_Y, gasketAbs.getZ() + HALF),
                Direction.UP, gasketAbs, false));
        BlockPos vatAbs = helper.absolutePos(RECEIVER_POS);
        helper.useBlock(RECEIVER_POS, player, new BlockHitResult(
                new Vec3(vatAbs.getX() + HALF, vatAbs.getY() + 1.0, vatAbs.getZ() + HALF),
                Direction.UP, vatAbs, false));
        return vat;
    }

    private static long vatWater(VatBlockEntity vat) {
        GooFluidHandler handler = vat.getFluidHandler();
        long water = 0;
        for (int i = 0; i < handler.size(); i++) {
            if (handler.getResource(i).getFluid().isSame(Fluids.WATER)) {
                water += handler.getAmountAsLong(i);
            }
        }
        return water;
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
                CanisterFluidContent.of(GooFluids.resource(GooTypes.BLAZE), OUTPUT_GOO));
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
        return handler == null ? 0 : handler.totalVolume();
    }

    private static int receiverAmount(CanisterBlockEntity receiver) {
        CanisterSlotFluidHandler handler = receiver.containerState().getSlotFluidHandler(CanisterBlock.CENTER_SLOT);
        return handler == null ? 0 : handler.totalVolume();
    }
}
