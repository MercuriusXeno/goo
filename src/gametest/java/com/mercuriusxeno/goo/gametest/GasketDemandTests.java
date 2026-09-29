package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.GooConstants;
import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.block.canister.CanisterBlock;
import com.mercuriusxeno.goo.block.canister.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.crystallizer.CrystallizerBlock;
import com.mercuriusxeno.goo.block.crystallizer.CrystallizerBlockEntity;
import com.mercuriusxeno.goo.block.crystallizer.CrystallizerPhases;
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
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.CanisterMetadata;
import com.mercuriusxeno.goo.item.gasket.GasketPartner;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooFluids;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;
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

    // --- Vat, canister, crystallizer chain (machine_bay: 5 wide, 3 deep) ---

    private static final BlockPos CRYSTALLIZER_POS = new BlockPos(3, 1, 1);
    private static final BlockPos MIDDLE_POS = new BlockPos(1, 1, 2);
    private static final BlockPos CHAIN_VAT_POS = new BlockPos(1, 1, 0);
    private static final int CATALYST_ROLE = 0;
    private static final int INGREDIENT_ROLE = 1;
    /** The knob size naming a kilochrysm. */
    private static final int KILOCHRYSM_KNOB = 2;
    /** Past a chrysm, so the pace, about 690 mB a tick, outruns the middle canister's taper rate of 64. */
    private static final long SEEDED_CRYSTALLIZED = 20_000L;
    /** The crystallizer's save key for what it has crystallized. */
    private static final String TAG_CRYSTALLIZED = "Crystallized";
    /** Crystal enough for the run: the pace climbs about 32-fold over 100 ticks. */
    private static final int CRYSTAL_HELD = 100_000;
    private static final int MIDDLE_HELD = 1_000;
    private static final int CHAIN_VAT_GOO = 2_000_000;
    /** Whole 10 mB steps and a pace carried across ticks keep growth a little behind the pace owed. */
    private static final double PACE_KEPT = 0.9;
    /** A tick's rounding up of the pace, over the run. */
    private static final int LEVEL_DRIFT = MEASURED_TICKS;

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

    /**
     * A vat feeding a canister that feeds a crystallizer's ingredient canister: the
     * crystallizer takes its pace each tick through the middle canister, whose level holds
     * where it started, though the taper rate of that level is far below the pace. The
     * vat, then the middle canister, then the crystallizer are placed, so each tick's flow
     * runs down the chain in that order and both links ask the same pace.
     *
     * @param helper the gametest helper
     */
    public static void crystallizerDrawsItsPaceThroughACanister(GameTestHelper helper) {
        VatBlockEntity vat = placeVat(helper, CHAIN_VAT_POS);
        vat.insertGoo(GooTypes.ENDER, CHAIN_VAT_GOO);
        helper.setBlock(MIDDLE_POS, GooBlocks.CANISTER.get());
        CanisterBlockEntity middle = helper.getBlockEntity(MIDDLE_POS, CanisterBlockEntity.class);
        UUID middleTop = UUID.randomUUID();
        UUID middleBottom = UUID.randomUUID();
        middle.insertCanister(CanisterBlock.CENTER_SLOT,
                canister(GooTypes.ENDER, MIDDLE_HELD, middleTop, middleBottom), false);

        CrystallizerBlockEntity crystallizer = placeSeededCrystallizer(helper);
        CanisterBlockEntity crystallizerCanisters = helper.getBlockEntity(CRYSTALLIZER_POS.above(),
                CanisterBlockEntity.class);
        int ingredientSlot = crystallizer.canisterSlot(INGREDIENT_ROLE);
        UUID ingredientTop = UUID.randomUUID();
        crystallizerCanisters.insertCanister(crystallizer.canisterSlot(CATALYST_ROLE),
                canister(GooTypes.CRYSTAL, CRYSTAL_HELD, null, null), false);
        crystallizerCanisters.insertCanister(ingredientSlot, canister(GooTypes.ENDER, 0, ingredientTop, null), false);

        GasketRegistry registry = GasketRegistry.get(helper.getLevel());
        UUID vatTransmitter = vat.ensureGasketId(GasketRole.TRANSMITTER);
        registry.link(vatTransmitter, middleTop);
        vat.setPartner(GasketRole.TRANSMITTER, new GasketPartner(helper.absolutePos(MIDDLE_POS), CanisterBlock.CENTER_SLOT));
        middle.setPartner(GasketRole.RECEIVER, CanisterBlock.CENTER_SLOT,
                new GasketPartner(helper.absolutePos(CHAIN_VAT_POS), GooConstants.NO_SLOT));
        registry.link(middleBottom, ingredientTop);
        middle.setPartner(GasketRole.TRANSMITTER, CanisterBlock.CENTER_SLOT,
                new GasketPartner(helper.absolutePos(CRYSTALLIZER_POS.above()), ingredientSlot));
        crystallizerCanisters.setPartner(GasketRole.RECEIVER, ingredientSlot,
                new GasketPartner(helper.absolutePos(MIDDLE_POS), CanisterBlock.CENTER_SLOT));

        helper.assertTrue(CrystallizerPhases.paceAllowance(SEEDED_CRYSTALLIZED) > GasketPushMath.taperRate(MIDDLE_HELD),
                "The pace should outrun the taper rate of the middle canister's level");
        long seeded = crystallizer.crystallized();
        double[] paceOwed = new double[1];
        helper.onEachTick(() -> paceOwed[0] += CrystallizerPhases.paceAllowance(crystallizer.crystallized()));
        helper.runAfterDelay(MEASURED_TICKS, () -> {
            long grown = crystallizer.crystallized() - seeded;
            helper.assertTrue(grown >= paceOwed[0] * PACE_KEPT,
                    "The crystallizer should grow at its pace: grew " + grown + " of " + (long) paceOwed[0]);
            int middleNow = middle.getSlotFluidContent(CanisterBlock.CENTER_SLOT).amount();
            helper.assertTrue(Math.abs(middleNow - MIDDLE_HELD) <= LEVEL_DRIFT,
                    "The middle canister's level should hold at " + MIDDLE_HELD + ", reads " + middleNow);
            helper.succeed();
        });
    }

    /**
     * Places the crystallizer with its knob at kilochrysm, a canister block on its top, and
     * reloads it from its saved data holding {@link #SEEDED_CRYSTALLIZED}, so its pace starts
     * past a chrysm.
     *
     * @param helper the gametest helper
     * @return the seeded crystallizer
     */
    private static CrystallizerBlockEntity placeSeededCrystallizer(GameTestHelper helper) {
        helper.setBlock(CRYSTALLIZER_POS, GooBlocks.CRYSTALLIZER.get().defaultBlockState()
                .setValue(CrystallizerBlock.FACING, Direction.NORTH).setValue(CrystallizerBlock.KNOB, KILOCHRYSM_KNOB));
        helper.setBlock(CRYSTALLIZER_POS.above(), GooBlocks.CANISTER.get());
        ServerLevel level = helper.getLevel();
        BlockEntity placed = helper.getBlockEntity(CRYSTALLIZER_POS, CrystallizerBlockEntity.class);
        CompoundTag saved = placed.saveWithFullMetadata(level.registryAccess());
        saved.putLong(TAG_CRYSTALLIZED, SEEDED_CRYSTALLIZED);
        level.removeBlockEntity(placed.getBlockPos());
        BlockEntity seeded = BlockEntity.loadStatic(placed.getBlockPos(), placed.getBlockState(), saved,
                level.registryAccess());
        helper.assertTrue(seeded instanceof CrystallizerBlockEntity, "The crystallizer should reload");
        level.setBlockEntity(seeded);
        CrystallizerBlockEntity crystallizer = (CrystallizerBlockEntity) seeded;
        helper.assertValueEqual(crystallizer.crystallized(), SEEDED_CRYSTALLIZED, "seeded crystallized");
        return crystallizer;
    }

    private static ItemStack canister(ResourceKey<GooTypeDefinition> type, int volume,
                                      @Nullable UUID topGasket, @Nullable UUID bottomGasket) {
        ItemStack canister = new ItemStack(GooItems.CANISTER.get());
        if (volume > 0) {
            CanisterItem.setFluidContent(canister, new CanisterFluidContent(GooFluids.resource(type), volume));
        }
        CanisterMetadata meta = CanisterItem.getMetadata(canister);
        if (topGasket != null) {
            meta = meta.withTopGasketId(topGasket);
        }
        if (bottomGasket != null) {
            meta = meta.withBottomGasketId(bottomGasket);
        }
        CanisterItem.setMetadata(canister, meta);
        return canister;
    }

    private static VatBlockEntity placeVat(GameTestHelper helper) {
        return placeVat(helper, VAT_POS);
    }

    private static VatBlockEntity placeVat(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, GooBlocks.VAT.get().defaultBlockState().setValue(VatBlock.GASKET_BASE, true));
        return helper.getBlockEntity(pos, VatBlockEntity.class);
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
