package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.GooConstants;
import com.mercuriusxeno.goo.block.canister.CanisterBlock;
import com.mercuriusxeno.goo.block.canister.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.crucible.CrucibleBlockEntity;
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
import com.mercuriusxeno.goo.item.ContainerCapacity;
import com.mercuriusxeno.goo.item.gasket.GasketPartner;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooFluids;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
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
import java.util.function.Consumer;
import java.util.function.IntSupplier;

/**
 * Receivers on the gasket network state their demand and their partner sends it
 * (decision receivers-demand-and-links-relay): a container with nothing demanding
 * behind it asks the power law of its capacity, a tap asks the rate its valve sets,
 * and a canister or vat between a source and a crystallizer asks its own rest plus the
 * crystallizer's pace upstream (decision relay-adds-dependent-ask-to-own).
 */
public final class GasketDemandTests {

    private static final BlockPos VAT_POS = new BlockPos(3, 1, 1);
    private static final BlockPos RECEIVER_POS = new BlockPos(1, 1, 1);
    /** Ticks each flow is measured over. */
    private static final int MEASURED_TICKS = 100;
    /** Enough that a canister's resting pull, about 4,200 mB a tick, cannot drain the vat within the run. */
    private static final int VAT_GOO = 1_000_000;
    private static final TapDripGrade VALVE = TapDripGrade.ONE_PER_4_TICKS;
    /** The drips the valve lets through over the run, allowing one drip of timing slack either way. */
    private static final int VALVE_DRIPS = MEASURED_TICKS / VALVE.intervalTicks();
    /** The drips the tap's own canister holds, well inside the run. */
    private static final int CANISTER_DRIPS = 5;

    // --- Vat, canister, crystallizer chain (machine_bay: 5 wide, 3 deep) ---

    private static final BlockPos CRYSTALLIZER_POS = new BlockPos(3, 1, 1);
    private static final BlockPos MIDDLE_POS = new BlockPos(1, 1, 2);
    private static final BlockPos CHAIN_VAT_POS = new BlockPos(1, 1, 0);
    private static final int CATALYST_ROLE = 0;
    private static final int INGREDIENT_ROLE = 1;
    /** The knob position naming a budding chrysm. */
    private static final int BUDDING_CHRYSM_KNOB = 2;
    /** Past a chrysm, so the budding pace, 968 mB a tick, outruns the middle canister's taper rate of 64. */
    private static final long SEEDED_CRYSTALLIZED = 100_000L;
    /** The crystallizer's save key for what it has crystallized. */
    private static final String TAG_CRYSTALLIZED = "Crystallized";
    /** Crystal enough for the run: a tenth of the budding pace over 100 ticks is under 10,000 mB. */
    private static final int CRYSTAL_HELD = 100_000;
    private static final int MIDDLE_HELD = 1_000;
    /** The hub canister that rests, lower than the feeder so slot order would fill it first. */
    private static final int RESTING_HUB_SLOT = 0;
    private static final int FEEDING_HUB_SLOT = 3;
    private static final int CHAIN_VAT_GOO = 2_000_000;
    /** Whole 10 mB steps and a pace carried across ticks keep growth a little behind the pace owed. */
    private static final double PACE_KEPT = 0.9;

    private GasketDemandTests() {
    }

    /**
     * A vat feeding a hub by gasket sends, every tick of the run, what the hub's canister
     * asks at rest, the power law of its own capacity, and the hub gains what the vat lost.
     *
     * @param helper the gametest helper
     */
    public static void vatFillsHubAtItsRestingDemand(GameTestHelper helper) {
        VatBlockEntity vat = placeVat(helper);
        helper.setBlock(RECEIVER_POS, GooBlocks.HUB.get().defaultBlockState().setValue(HubBlock.HAS_GASKET, true));
        HubBlockEntity hub = helper.getBlockEntity(RECEIVER_POS, HubBlockEntity.class);
        hub.insertCanisterAnywhere(new ItemStack(GooItems.CANISTER.get()));
        link(helper, vat, hub, RECEIVER_POS);
        vat.insertGoo(GooTypes.BLAZE, VAT_GOO);
        int restingPull = GasketPushMath.taperRate(ContainerCapacity.canisterCapacity(0), GasketPushMath.GOO_EXPONENT);
        AtomicInteger held = new AtomicInteger(VAT_GOO);
        AtomicInteger ticksSinceFirstSend = new AtomicInteger();
        AtomicInteger sendingTicks = new AtomicInteger();
        helper.onEachTick(() -> {
            int now = vat.getContents().getVolume(GooTypes.BLAZE);
            int sent = held.getAndSet(now) - now;
            if (sent > 0) {
                helper.assertValueEqual(sent, restingPull, "mB the hub's canister asked at rest");
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
     * valve drips: at one drip per 4 ticks, 25 mB over 100 ticks. The tap drips it into
     * the crucible below, so the crucible stocks the vat's type (decision
     * tap-asks-gasket-partner-per-drip).
     *
     * @param helper the gametest helper
     */
    public static void vatFeedsTapAtTheValveRate(GameTestHelper helper) {
        VatBlockEntity vat = placeVat(helper);
        TapBlockEntity tap = placeGasketedTapOverCrucible(helper);
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
            int stocked = crucibleHolds(helper, GooTypes.BLAZE);
            helper.assertTrue(stocked > 0 && stocked <= intake,
                    "The crucible should stock what the tap drew from the vat, " + intake + " mB, stocks " + stocked);
            helper.succeed();
        });
    }

    /**
     * A gasketed tap whose canister holds a few drips of goo drips those first and asks
     * the vat nothing, then once the canister is empty asks the vat for every drip
     * (decision tap-asks-gasket-partner-per-drip).
     *
     * @param helper the gametest helper
     */
    public static void tapDrainsItsCanisterBeforeAskingTheVat(GameTestHelper helper) {
        VatBlockEntity vat = placeVat(helper);
        TapBlockEntity tap = placeGasketedTapOverCrucible(helper);
        tap.insertCanister(new ItemStack(GooItems.CANISTER.get()));
        tap.insertGoo(GooTypes.BLAZE, CANISTER_DRIPS);
        link(helper, vat, tap, RECEIVER_POS);
        vat.insertGoo(GooTypes.BLAZE, VAT_GOO);
        helper.onEachTick(() -> {
            if (tap.getSlotGooType(TapBlockEntity.SLOT) != null) {
                helper.assertValueEqual(vat.getContents().getVolume(GooTypes.BLAZE), VAT_GOO,
                        "vat goo while the tap's canister holds goo");
            }
        });
        helper.runAfterDelay(MEASURED_TICKS, () -> {
            helper.assertValueEqual(tap.getFluidContent().totalVolume(), 0, "tap canister after the run");
            int asked = VAT_GOO - vat.getContents().getVolume(GooTypes.BLAZE);
            int afterCanister = VALVE_DRIPS - CANISTER_DRIPS;
            helper.assertTrue(Math.abs(asked - afterCanister) <= VALVE.dripVolume(),
                    "Once its canister ran dry the tap should ask " + afterCanister + " mB, asked " + asked);
            helper.succeed();
        });
    }

    /**
     * A vat holding blaze and unstable feeding a gasketed tap over a crucible sends the
     * types by turns, so the crucible stocks both (decision vat-round-robins-gasket-send).
     *
     * @param helper the gametest helper
     */
    public static void vatFeedsTapBothTypesByTurns(GameTestHelper helper) {
        VatBlockEntity vat = placeVat(helper);
        TapBlockEntity tap = placeGasketedTapOverCrucible(helper);
        link(helper, vat, tap, RECEIVER_POS);
        vat.insertGoo(GooTypes.BLAZE, VAT_GOO);
        vat.insertGoo(GooTypes.UNSTABLE, VAT_GOO);
        helper.runAfterDelay(MEASURED_TICKS, () -> {
            int blaze = crucibleHolds(helper, GooTypes.BLAZE);
            int unstable = crucibleHolds(helper, GooTypes.UNSTABLE);
            helper.assertTrue(blaze > 0 && unstable > 0,
                    "The crucible should stock both types, holds blaze " + blaze + " and unstable " + unstable);
            helper.succeed();
        });
    }

    /**
     * Stands a crucible, and over it an open gasketed tap with an empty slot at the valve grade.
     *
     * @param helper the gametest helper
     * @return the tap
     */
    private static TapBlockEntity placeGasketedTapOverCrucible(GameTestHelper helper) {
        helper.setBlock(RECEIVER_POS.below(), GooBlocks.CRUCIBLE.get());
        helper.setBlock(RECEIVER_POS, GooBlocks.TAP.get().defaultBlockState()
                .setValue(TapBlock.OPEN, true).setValue(TapBlock.HAS_GASKET, true));
        TapBlockEntity tap = helper.getBlockEntity(RECEIVER_POS, TapBlockEntity.class);
        tap.setDripGrade(VALVE);
        return tap;
    }

    private static int crucibleHolds(GameTestHelper helper, ResourceKey<GooTypeDefinition> type) {
        return helper.getBlockEntity(RECEIVER_POS.below(), CrucibleBlockEntity.class).reservoirHandler().getVolume(type);
    }

    /**
     * A vat feeding a canister that feeds a crystallizer's ingredient canister: the
     * crystallizer takes its pace each tick through the middle canister, though the taper
     * rate of that canister's level is far below the pace, and the canister fills on its own ask.
     *
     * @param helper the gametest helper
     */
    public static void crystallizerDrawsItsPaceThroughACanister(GameTestHelper helper) {
        VatBlockEntity source = placeVat(helper, CHAIN_VAT_POS);
        source.insertGoo(GooTypes.ENDER, CHAIN_VAT_GOO);
        helper.setBlock(MIDDLE_POS, GooBlocks.CANISTER.get());
        CanisterBlockEntity middle = helper.getBlockEntity(MIDDLE_POS, CanisterBlockEntity.class);
        UUID middleTop = UUID.randomUUID();
        UUID middleBottom = UUID.randomUUID();
        middle.insertCanister(CanisterBlock.CENTER_SLOT,
                canister(GooTypes.ENDER, MIDDLE_HELD, middleTop, middleBottom), false);
        GasketRegistry.get(helper.getLevel()).link(source.ensureGasketId(GasketRole.TRANSMITTER), middleTop);
        source.setPartner(GasketRole.TRANSMITTER,
                new GasketPartner(helper.absolutePos(MIDDLE_POS), CanisterBlock.CENTER_SLOT));
        middle.setPartner(GasketRole.RECEIVER, CanisterBlock.CENTER_SLOT,
                new GasketPartner(helper.absolutePos(CHAIN_VAT_POS), GooConstants.NO_SLOT));
        assertCrystallizerDrawsThrough(helper, middleBottom, CanisterBlock.CENTER_SLOT,
                outlet -> middle.setPartner(GasketRole.TRANSMITTER, CanisterBlock.CENTER_SLOT, outlet),
                () -> middle.getSlotFluidContent(CanisterBlock.CENTER_SLOT).totalVolume());
    }

    /**
     * A vat feeding a vat that feeds a crystallizer's ingredient canister: the middle vat
     * asks its own rest plus the crystallizer's pace upstream, so the crystallizer takes its
     * pace each tick and the middle vat fills.
     *
     * @param helper the gametest helper
     */
    public static void crystallizerDrawsItsPaceThroughAVat(GameTestHelper helper) {
        VatBlockEntity source = placeVat(helper, CHAIN_VAT_POS);
        source.insertGoo(GooTypes.ENDER, CHAIN_VAT_GOO);
        helper.setBlock(MIDDLE_POS, GooBlocks.VAT.get().defaultBlockState()
                .setValue(VatBlock.GASKET_CAP, true).setValue(VatBlock.GASKET_BASE, true));
        VatBlockEntity middle = helper.getBlockEntity(MIDDLE_POS, VatBlockEntity.class);
        middle.insertGoo(GooTypes.ENDER, MIDDLE_HELD);
        UUID middleIntake = middle.ensureGasketId(GasketRole.RECEIVER);
        UUID middleOutlet = middle.ensureGasketId(GasketRole.TRANSMITTER);
        GasketRegistry.get(helper.getLevel()).link(source.ensureGasketId(GasketRole.TRANSMITTER), middleIntake);
        source.setPartner(GasketRole.TRANSMITTER, new GasketPartner(helper.absolutePos(MIDDLE_POS), GooConstants.NO_SLOT));
        middle.setPartner(GasketRole.RECEIVER, new GasketPartner(helper.absolutePos(CHAIN_VAT_POS), GooConstants.NO_SLOT));
        assertCrystallizerDrawsThrough(helper, middleOutlet, GooConstants.NO_SLOT,
                outlet -> middle.setPartner(GasketRole.TRANSMITTER, outlet),
                () -> middle.getContents().getVolume(GooTypes.ENDER));
    }

    /**
     * A vat feeding a hub's intake, the hub holding two canisters of ender: the lower
     * canister rests with room, and the higher one's bottom gasket feeds a crystallizer.
     * The hub gives each canister the demand it states, so the crystallizer grows at its
     * pace and the resting canister gains no more than its resting demand a tick.
     *
     * @param helper the gametest helper
     */
    public static void hubGivesEachCanisterItsOwnDemand(GameTestHelper helper) {
        VatBlockEntity source = placeVat(helper, CHAIN_VAT_POS);
        source.insertGoo(GooTypes.ENDER, CHAIN_VAT_GOO);
        helper.setBlock(MIDDLE_POS, GooBlocks.HUB.get().defaultBlockState().setValue(HubBlock.HAS_GASKET, true));
        HubBlockEntity hub = helper.getBlockEntity(MIDDLE_POS, HubBlockEntity.class);
        UUID feederBottom = UUID.randomUUID();
        hub.insertCanister(RESTING_HUB_SLOT, canister(GooTypes.ENDER, MIDDLE_HELD, null, null));
        hub.insertCanister(FEEDING_HUB_SLOT, canister(GooTypes.ENDER, MIDDLE_HELD, null, feederBottom));
        UUID intake = hub.ensureGasketId(GasketRole.RECEIVER);
        GasketRegistry.get(helper.getLevel()).link(source.ensureGasketId(GasketRole.TRANSMITTER), intake);
        source.setPartner(GasketRole.TRANSMITTER, new GasketPartner(helper.absolutePos(MIDDLE_POS), GooConstants.NO_SLOT));
        hub.setPartner(GasketRole.RECEIVER, new GasketPartner(helper.absolutePos(CHAIN_VAT_POS), GooConstants.NO_SLOT));

        int restingPull = GasketPushMath.taperRate(ContainerCapacity.canisterCapacity(0), GasketPushMath.GOO_EXPONENT);
        AtomicInteger restingHeld = new AtomicInteger(MIDDLE_HELD);
        helper.onEachTick(() -> {
            int now = hubSlotHolds(hub, RESTING_HUB_SLOT);
            int gained = now - restingHeld.getAndSet(now);
            helper.assertTrue(gained <= restingPull,
                    "The resting canister should gain at most its resting demand, " + restingPull + ", gained " + gained);
        });
        assertCrystallizerDrawsThrough(helper, feederBottom, FEEDING_HUB_SLOT,
                outlet -> hub.setPartner(GasketRole.TRANSMITTER, FEEDING_HUB_SLOT, outlet),
                () -> hubSlotHolds(hub, FEEDING_HUB_SLOT));
    }

    private static int hubSlotHolds(HubBlockEntity hub, int slot) {
        return (int) hub.getFluidHandler().getAmountAsLong(slot);
    }

    /**
     * Stands the seeded crystallizer, links the middle link's outlet to its ingredient
     * canister, and asserts over the run that the crystallizer grows at its pace while the
     * middle link fills on its own ask. The source, then the middle link, then the crystallizer
     * are placed, so each tick's flow runs down the chain in that order.
     *
     * @param helper       the gametest helper
     * @param middleOutlet the middle link's transmitter gasket id
     * @param middleSlot   the middle link's slot, or no slot for a vat
     * @param linkOutlet   points the middle link's transmitter at a partner
     * @param middleLevel  reads the middle link's ender
     */
    private static void assertCrystallizerDrawsThrough(GameTestHelper helper, UUID middleOutlet, int middleSlot,
                                                       Consumer<GasketPartner> linkOutlet, IntSupplier middleLevel) {
        CrystallizerBlockEntity crystallizer = placeSeededCrystallizer(helper);
        CanisterBlockEntity crystallizerCanisters = helper.getBlockEntity(CRYSTALLIZER_POS.above(),
                CanisterBlockEntity.class);
        int ingredientSlot = crystallizer.canisterSlot(INGREDIENT_ROLE);
        UUID ingredientTop = UUID.randomUUID();
        crystallizerCanisters.insertCanister(crystallizer.canisterSlot(CATALYST_ROLE),
                canister(GooTypes.CRYSTAL, CRYSTAL_HELD, null, null), false);
        crystallizerCanisters.insertCanister(ingredientSlot, canister(GooTypes.ENDER, 0, ingredientTop, null), false);
        GasketRegistry.get(helper.getLevel()).link(middleOutlet, ingredientTop);
        linkOutlet.accept(new GasketPartner(helper.absolutePos(CRYSTALLIZER_POS.above()), ingredientSlot));
        crystallizerCanisters.setPartner(GasketRole.RECEIVER, ingredientSlot,
                new GasketPartner(helper.absolutePos(MIDDLE_POS), middleSlot));

        helper.assertTrue(CrystallizerPhases.paceAllowance(SEEDED_CRYSTALLIZED) > GasketPushMath.taperRate(MIDDLE_HELD),
                "The pace should outrun the taper rate of the middle link's level");
        long seeded = crystallizer.crystallized();
        double[] paceOwed = new double[1];
        helper.onEachTick(() -> paceOwed[0] += CrystallizerPhases.paceAllowance(crystallizer.crystallized()));
        helper.runAfterDelay(MEASURED_TICKS, () -> {
            long grown = crystallizer.crystallized() - seeded;
            helper.assertTrue(grown >= paceOwed[0] * PACE_KEPT,
                    "The crystallizer should grow at its pace: grew " + grown + " of " + (long) paceOwed[0]);
            int middleNow = middleLevel.getAsInt();
            helper.assertTrue(middleNow > MIDDLE_HELD,
                    "The middle link should fill past " + MIDDLE_HELD + " on its own ask, reads " + middleNow);
            helper.succeed();
        });
    }

    /**
     * Places the crystallizer with its knob at budding chrysm, a canister block on its top, and
     * reloads it from its saved data holding {@link #SEEDED_CRYSTALLIZED}, so its pace starts
     * past a chrysm.
     *
     * @param helper the gametest helper
     * @return the seeded crystallizer
     */
    private static CrystallizerBlockEntity placeSeededCrystallizer(GameTestHelper helper) {
        helper.setBlock(CRYSTALLIZER_POS, GooBlocks.CRYSTALLIZER.get().defaultBlockState()
                .setValue(CrystallizerBlock.FACING, Direction.NORTH).setValue(CrystallizerBlock.KNOB, BUDDING_CHRYSM_KNOB));
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
            CanisterItem.setFluidContent(canister, CanisterFluidContent.of(GooFluids.resource(type), volume));
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
