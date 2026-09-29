package com.mercuriusxeno.goo.block.canister;

import com.mercuriusxeno.goo.block.gasket.GasketDemand;
import com.mercuriusxeno.goo.block.gasket.GasketPusher;
import com.mercuriusxeno.goo.fluid.GooFluid;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import java.util.OptionalInt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * A canister slot relays to its source the demand of the consumer it feeds
 * (decision receivers-demand-and-links-relay): the machine drawing on it, else the
 * partner behind its bottom gasket, else none, so its source sends the power-law
 * default. The pusher is a mock; the vanilla bootstrap stands the water fluid the
 * default's exponent is read against.
 */
class CanisterSlotRelayTest {

    private static final int PARTNER_DEMAND = 242_000;
    private static final int MACHINE_DEMAND = 7;
    private static final int SOURCE_HOLDS = 1_000_000;

    private FluidResource goo;
    private CanisterSlotFluidHandler handler;

    @BeforeAll
    static void standVanillaFluids() {
        try (MockedStatic<FMLLoader> loader = mockStatic(FMLLoader.class, RETURNS_DEEP_STUBS)) {
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
        }
    }

    @BeforeEach
    void standGoo() {
        goo = mock(FluidResource.class);
        when(goo.getFluid()).thenReturn(mock(GooFluid.Source.class));
        handler = new CanisterSlotFluidHandler(SOURCE_HOLDS, () -> { });
    }

    private CanisterSlot slotStatingMachineDemand(OptionalInt machine) {
        return new CanisterSlot(0, mock(VoxelShape.class), () -> { }, () -> { }, incoming -> true,
                incoming -> machine);
    }

    private static GasketPusher pusherWhosePartnerStates(OptionalInt demand, FluidResource resource) {
        GasketPusher pusher = mock(GasketPusher.class);
        when(pusher.partnerStatedDemand(resource)).thenReturn(demand);
        return pusher;
    }

    @Test
    void aSlotFeedingAPartnerAnswersThatPartnersDemand() {
        CanisterSlot slot = slotStatingMachineDemand(OptionalInt.empty());
        slot.setPusher(pusherWhosePartnerStates(OptionalInt.of(PARTNER_DEMAND), goo));
        handler.setConsumerDemand(slot::consumerDemand);
        assertEquals(PARTNER_DEMAND, GasketDemand.demandOf(handler, goo, SOURCE_HOLDS));
    }

    @Test
    void aSlotWithNothingBehindItAnswersTheTaperRate() {
        CanisterSlot slot = slotStatingMachineDemand(OptionalInt.empty());
        handler.setConsumerDemand(slot::consumerDemand);
        assertEquals(GasketDemand.defaultDemand(goo, SOURCE_HOLDS), GasketDemand.demandOf(handler, goo, SOURCE_HOLDS));
    }

    @Test
    void aSlotWhosePartnerStatesNoneAnswersTheTaperRate() {
        CanisterSlot slot = slotStatingMachineDemand(OptionalInt.empty());
        slot.setPusher(pusherWhosePartnerStates(OptionalInt.empty(), goo));
        handler.setConsumerDemand(slot::consumerDemand);
        assertEquals(GasketDemand.defaultDemand(goo, SOURCE_HOLDS), GasketDemand.demandOf(handler, goo, SOURCE_HOLDS));
    }

    @Test
    void theMachineDrawingOnTheSlotIsTheConsumerItRelays() {
        CanisterSlot slot = slotStatingMachineDemand(OptionalInt.of(MACHINE_DEMAND));
        slot.setPusher(pusherWhosePartnerStates(OptionalInt.of(PARTNER_DEMAND), goo));
        handler.setConsumerDemand(slot::consumerDemand);
        assertEquals(MACHINE_DEMAND, GasketDemand.demandOf(handler, goo, SOURCE_HOLDS));
    }

    @Test
    void aLoopOfLinksFallsBackToTheTaperRate() {
        handler.setConsumerDemand(resource -> GasketDemand.statedDemandOf(handler, resource));
        assertEquals(GasketDemand.defaultDemand(goo, SOURCE_HOLDS), GasketDemand.demandOf(handler, goo, SOURCE_HOLDS));
    }
}
