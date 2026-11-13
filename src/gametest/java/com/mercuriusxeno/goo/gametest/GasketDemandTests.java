package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.GooConstants;
import com.mercuriusxeno.goo.block.gasket.GasketPushMath;
import com.mercuriusxeno.goo.block.gasket.IGasketHolder;
import com.mercuriusxeno.goo.block.hub.HubBlock;
import com.mercuriusxeno.goo.block.hub.HubBlockEntity;
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
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Receivers on the gasket network state their demand and their partner sends it
 * (decision receivers-demand-and-links-relay): a container with nothing demanding
 * behind it asks the power law of its capacity, and a canister between a source and a vat asks its own rest plus the
 * vat's ask upstream (decision relay-adds-dependent-ask-to-own).
 */
@RegistersGameTests
public final class GasketDemandTests {
    private static final String GASKET_DEMAND_VAT_FILLS_HUB = "gasket_demand_vat_fills_hub";
    private static final String GASKET_DEMAND_HUB_SHARES = "gasket_demand_hub_shares";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, GASKET_DEMAND_VAT_FILLS_HUB, GasketDemandTests::vatFillsHubAtItsRestingDemand);
        GooTestFunctions.registerTest(registrar, GASKET_DEMAND_HUB_SHARES, GasketDemandTests::hubGivesEachCanisterItsOwnDemand);
    }


    private static final BlockPos VAT_POS = new BlockPos(3, 1, 1);
    private static final BlockPos RECEIVER_POS = new BlockPos(1, 1, 1);
    /** Ticks each flow is measured over. */
    private static final int MEASURED_TICKS = 100;
    /** Enough that a canister's resting pull, about 4,200 mB a tick, cannot drain the vat within the run. */
    private static final int VAT_GOO = 1_000_000;

    // --- Vat, hub, vat chain (machine_bay: 5 wide, 3 deep) ---

    private static final BlockPos HUB_POS = new BlockPos(1, 1, 2);
    private static final BlockPos SOURCE_VAT_POS = new BlockPos(1, 1, 0);
    private static final BlockPos FED_VAT_POS = new BlockPos(3, 1, 1);
    private static final int SOURCE_VAT_GOO = 2_000_000;
    private static final int HUB_CANISTER_HELD = 1_000;
    /** The hub canister that rests, lower than the feeder so slot order would fill it first. */
    private static final int RESTING_HUB_SLOT = 0;
    private static final int FEEDING_HUB_SLOT = 3;
    private GasketDemandTests() {
    }

    /**
     * A vat feeding a hub by gasket sends, on each tick it sends, what the hub's canister
     * asks at rest, the power law of its own capacity.
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
        AtomicInteger sendingTicks = new AtomicInteger();
        helper.onEachTick(() -> {
            int now = vat.getContents().getVolume(GooTypes.BLAZE);
            int sent = held.getAndSet(now) - now;
            if (sent > 0) {
                helper.assertValueEqual(sent, restingPull, "mB the hub's canister asked at rest");
                sendingTicks.incrementAndGet();
            }
        });
        helper.runAfterDelay(MEASURED_TICKS, () -> {
            helper.assertTrue(sendingTicks.get() > 0, "The vat should send to the hub's canister");
            helper.succeed();
        });
    }


    /**
     * A vat feeding a hub's intake, the hub holding two canisters of ender: the lower
     * canister rests with room, and the higher one's bottom gasket feeds a second vat.
     * The hub gives each canister the demand it states: the feeder, asking for the fed vat
     * beside its own rest, takes more than the resting canister, which still takes its own;
     * HubFluidHandlerDemandTest holds each canister's share a tick.
     * decision relay-adds-dependent-ask-to-own
     *
     * @param helper the gametest helper
     */
    public static void hubGivesEachCanisterItsOwnDemand(GameTestHelper helper) {
        VatBlockEntity source = placeVat(helper, SOURCE_VAT_POS);
        source.insertGoo(GooTypes.ENDER, SOURCE_VAT_GOO);
        helper.setBlock(HUB_POS, GooBlocks.HUB.get().defaultBlockState().setValue(HubBlock.HAS_GASKET, true));
        HubBlockEntity hub = helper.getBlockEntity(HUB_POS, HubBlockEntity.class);
        UUID feederBottom = UUID.randomUUID();
        hub.insertCanister(RESTING_HUB_SLOT, canister(GooTypes.ENDER, HUB_CANISTER_HELD, null));
        hub.insertCanister(FEEDING_HUB_SLOT, canister(GooTypes.ENDER, HUB_CANISTER_HELD, feederBottom));
        UUID intake = hub.ensureGasketId(GasketRole.RECEIVER);
        GasketRegistry.get(helper.getLevel()).link(source.ensureGasketId(GasketRole.TRANSMITTER), intake);
        source.setPartner(GasketRole.TRANSMITTER, new GasketPartner(helper.absolutePos(HUB_POS), GooConstants.NO_SLOT));
        hub.setPartner(GasketRole.RECEIVER, new GasketPartner(helper.absolutePos(SOURCE_VAT_POS), GooConstants.NO_SLOT));

        helper.setBlock(FED_VAT_POS, GooBlocks.VAT.get().defaultBlockState().setValue(VatBlock.GASKET_CAP, true));
        VatBlockEntity fed = helper.getBlockEntity(FED_VAT_POS, VatBlockEntity.class);
        GasketRegistry.get(helper.getLevel()).link(feederBottom, fed.ensureGasketId(GasketRole.RECEIVER));
        hub.setPartner(GasketRole.TRANSMITTER, FEEDING_HUB_SLOT,
                new GasketPartner(helper.absolutePos(FED_VAT_POS), GooConstants.NO_SLOT));
        fed.setPartner(GasketRole.RECEIVER, new GasketPartner(helper.absolutePos(HUB_POS), FEEDING_HUB_SLOT));

        int restingPull = GasketPushMath.taperRate(ContainerCapacity.canisterCapacity(0), GasketPushMath.GOO_EXPONENT);
        helper.runAfterDelay(MEASURED_TICKS, () -> {
            int fedHolds = fed.getContents().getVolume(GooTypes.ENDER);
            helper.assertTrue(fedHolds > restingPull,
                    "The fed vat should fill through the feeding canister past one resting pull, holds " + fedHolds);
            int passedThroughFeeder = fedHolds + hubSlotHolds(hub, FEEDING_HUB_SLOT) - HUB_CANISTER_HELD;
            int restingGained = hubSlotHolds(hub, RESTING_HUB_SLOT) - HUB_CANISTER_HELD;
            helper.assertTrue(restingGained > 0, "The resting canister should still take its own demand");
            helper.assertTrue(passedThroughFeeder > restingGained,
                    "The feeder, asking for the fed vat beside itself, should take more than the resting canister: took "
                            + passedThroughFeeder + " against " + restingGained);
            helper.succeed();
        });
    }

    private static int hubSlotHolds(HubBlockEntity hub, int slot) {
        return (int) hub.getFluidHandler().getAmountAsLong(slot);
    }

    private static ItemStack canister(ResourceKey<GooTypeDefinition> type, int volume, @Nullable UUID bottomGasket) {
        ItemStack canister = new ItemStack(GooItems.CANISTER.get());
        if (volume > 0) {
            CanisterItem.setFluidContent(canister, CanisterFluidContent.of(GooFluids.resource(type), volume));
        }
        CanisterMetadata meta = CanisterItem.getMetadata(canister);
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

}
