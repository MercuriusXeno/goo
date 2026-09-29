package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.GooConstants;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.block.gasket.GasketPushMath;
import com.mercuriusxeno.goo.block.gasket.IGasketHolder;
import com.mercuriusxeno.goo.block.hub.HubBlock;
import com.mercuriusxeno.goo.block.hub.HubBlockEntity;
import com.mercuriusxeno.goo.block.tap.TapBlock;
import com.mercuriusxeno.goo.block.tap.TapBlockEntity;
import com.mercuriusxeno.goo.block.tap.TapDripGrade;
import com.mercuriusxeno.goo.block.vat.VatBlock;
import com.mercuriusxeno.goo.block.vat.VatBlockEntity;
import com.mercuriusxeno.goo.data.GasketRegistry;
import com.mercuriusxeno.goo.item.gasket.GasketPartner;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Receivers on the gasket network state their demand and their partner sends it
 * (decision receivers-demand-and-links-relay): a hub with no consumer behind it
 * asks the power-law rate, and a tap asks the rate its valve sets.
 */
public final class GasketDemandTests {

    private static final BlockPos VAT_POS = new BlockPos(3, 1, 1);
    private static final BlockPos RECEIVER_POS = new BlockPos(1, 1, 1);
    /** Ticks each flow is measured over. */
    private static final int MEASURED_TICKS = 100;
    /** Enough that the taper rate cannot drain the vat within the run. */
    private static final int VAT_GOO = 100_000;
    private static final TapDripGrade VALVE = TapDripGrade.ONE_PER_4_TICKS;
    /** The drips the valve lets through over the run, allowing one drip of timing slack either way. */
    private static final int VALVE_DRIPS = MEASURED_TICKS / VALVE.intervalTicks();

    private GasketDemandTests() {
    }

    /**
     * A vat feeding a hub by gasket sends, every tick of the run, the taper rate of
     * what it held, and the hub gains what the vat lost.
     *
     * @param helper the gametest helper
     */
    public static void vatFillsHubAtTheTaperRate(GameTestHelper helper) {
        VatBlockEntity vat = placeVat(helper);
        helper.setBlock(RECEIVER_POS, GooBlocks.HUB.get().defaultBlockState().setValue(HubBlock.HAS_GASKET, true));
        HubBlockEntity hub = helper.getBlockEntity(RECEIVER_POS, HubBlockEntity.class);
        hub.insertCanisterAnywhere(new ItemStack(GooItems.CANISTER.get()));
        link(helper, vat, hub, RECEIVER_POS);
        vat.insertGoo(GooTypes.BLAZE, VAT_GOO);
        AtomicInteger held = new AtomicInteger(VAT_GOO);
        AtomicInteger ticksSinceFirstSend = new AtomicInteger();
        AtomicInteger sendingTicks = new AtomicInteger();
        helper.onEachTick(() -> {
            int now = vat.getContents().getVolume(GooTypes.BLAZE);
            int sent = held.getAndSet(now) - now;
            if (sent > 0) {
                helper.assertValueEqual(sent, GasketPushMath.taperRate(now + sent), "mB sent from " + (now + sent));
                sendingTicks.incrementAndGet();
            }
            if (sendingTicks.get() > 0) {
                ticksSinceFirstSend.incrementAndGet();
            }
        });
        helper.runAfterDelay(MEASURED_TICKS, () -> {
            helper.assertTrue(sendingTicks.get() > 0 && sendingTicks.get() == ticksSinceFirstSend.get(),
                    "Once sending, the vat should send every tick: sent on " + sendingTicks.get()
                            + " of " + ticksSinceFirstSend.get());
            helper.assertValueEqual(hubHolds(hub), VAT_GOO - vat.getContents().getVolume(GooTypes.BLAZE),
                    "hub gain against vat loss");
            helper.succeed();
        });
    }

    /**
     * A vat feeding an open tap with an empty slot by gasket sends the tap what its
     * valve drips: at one drip per 4 ticks, 25 mB over 100 ticks.
     *
     * @param helper the gametest helper
     */
    public static void vatFeedsTapAtTheValveRate(GameTestHelper helper) {
        VatBlockEntity vat = placeVat(helper);
        helper.setBlock(RECEIVER_POS.below(), GooBlocks.CRUCIBLE.get());
        helper.setBlock(RECEIVER_POS, GooBlocks.TAP.get().defaultBlockState()
                .setValue(TapBlock.OPEN, true).setValue(TapBlock.HAS_GASKET, true));
        TapBlockEntity tap = helper.getBlockEntity(RECEIVER_POS, TapBlockEntity.class);
        tap.setDripGrade(VALVE);
        link(helper, vat, tap, RECEIVER_POS);
        vat.insertGoo(GooTypes.BLAZE, VAT_GOO);
        AtomicInteger held = new AtomicInteger(VAT_GOO);
        helper.onEachTick(() -> {
            int now = vat.getContents().getVolume(GooTypes.BLAZE);
            int sent = held.getAndSet(now) - now;
            helper.assertTrue(sent <= VALVE.dripVolume(), "The tap asked more than one drip in a tick: " + sent);
        });
        helper.runAfterDelay(MEASURED_TICKS, () -> {
            int intake = VAT_GOO - vat.getContents().getVolume(GooTypes.BLAZE);
            helper.assertTrue(Math.abs(intake - VALVE_DRIPS) <= VALVE.dripVolume(),
                    "The tap should take " + VALVE_DRIPS + " mB over " + MEASURED_TICKS + " ticks, took " + intake);
            helper.succeed();
        });
    }

    private static VatBlockEntity placeVat(GameTestHelper helper) {
        helper.setBlock(VAT_POS, GooBlocks.VAT.get().defaultBlockState().setValue(VatBlock.GASKET_BASE, true));
        return helper.getBlockEntity(VAT_POS, VatBlockEntity.class);
    }

    /**
     * Links the vat's base gasket to the receiver's gasket the way the tuner leaves them.
     *
     * @param helper      the gametest helper
     * @param vat         the sending vat
     * @param receiver    the receiving machine
     * @param receiverPos where the receiver stands
     */
    private static void link(GameTestHelper helper, VatBlockEntity vat, IGasketHolder receiver, BlockPos receiverPos) {
        UUID transmitter = vat.ensureGasketId(GasketRole.TRANSMITTER);
        UUID intake = receiver.ensureGasketId(GasketRole.RECEIVER);
        helper.assertTrue(transmitter != null && intake != null, "Both gaskets should hold ids");
        GasketRegistry.get(helper.getLevel()).link(transmitter, intake);
        receiver.setPartner(GasketRole.RECEIVER, new GasketPartner(helper.absolutePos(VAT_POS), GooConstants.NO_SLOT));
        vat.setPartner(GasketRole.TRANSMITTER, new GasketPartner(helper.absolutePos(receiverPos), GooConstants.NO_SLOT));
    }

    private static int hubHolds(HubBlockEntity hub) {
        long total = 0;
        for (int i = 0; i < hub.getFluidHandler().size(); i++) {
            total += hub.getFluidHandler().getAmountAsLong(i);
        }
        return Math.toIntExact(total);
    }
}
