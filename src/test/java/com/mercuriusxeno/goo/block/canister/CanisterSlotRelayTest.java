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
 * A canister slot asks its source the power law of its own capacity plus the demand of
 * the consumer it feeds (decision relay-adds-dependent-ask-to-own): the machine drawing
 * on it, else the partner behind its bottom gasket. The pusher is a mock; the vanilla bootstrap stands the water
 * fluid the exponent is read against.
 */
class CanisterSlotRelayTest {

    private static final int PARTNER_DEMAND = 242_000;
    private static final int MACHINE_DEMAND = 7;
    private static final int CAPACITY = 1 << 20;

    private FluidResource goo;
    private CanisterSlotFluidHandler handler;
    private int resting;

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
        handler = new CanisterSlotFluidHandler(CAPACITY, () -> { });
        resting = GasketDemand.restingDemand(goo, CAPACITY, 0);
    }

    private static CanisterSlot slotStatingMachineDemand(OptionalInt machine) {
        return new CanisterSlot(0, mock(VoxelShape.class), () -> { }, () -> { }, incoming -> true,
                incoming -> machine);
    }

    private static GasketPusher pusherWhosePartnerStates(OptionalInt demand, FluidResource resource) {
        GasketPusher pusher = mock(GasketPusher.class);
        when(pusher.partnerStatedDemand(resource)).thenReturn(demand);
        return pusher;
    }

    private CanisterSlot slotFeedingAPartner(OptionalInt machine) {
        CanisterSlot slot = slotStatingMachineDemand(machine);
        slot.setPusher(pusherWhosePartnerStates(OptionalInt.of(PARTNER_DEMAND), goo));
        return slot;
    }

    @Test
    void aSlotWithNoMachineReadsItsConsumerFromThePartnerBehindItsGasket() {
        assertEquals(OptionalInt.of(PARTNER_DEMAND), slotFeedingAPartner(OptionalInt.empty()).consumerDemand(goo));
    }

    @Test
    void aCanisterAsksItsRestPlusItsConsumersDemand() {
        handler.setConsumerDemand(resource -> OptionalInt.of(PARTNER_DEMAND));
        assertEquals(resting + PARTNER_DEMAND, GasketDemand.demandOf(handler, goo));
    }

    @Test
    void aSlotWithNothingBehindItRestsAtThePowerLawOfItsCapacity() {
        CanisterSlot slot = slotStatingMachineDemand(OptionalInt.empty());
        handler.setConsumerDemand(slot::consumerDemand);
        assertEquals(resting, GasketDemand.demandOf(handler, goo));
    }

    @Test
    void theMachineDrawingOnTheSlotIsItsConsumerAheadOfThePartner() {
        assertEquals(OptionalInt.of(MACHINE_DEMAND), slotFeedingAPartner(OptionalInt.of(MACHINE_DEMAND)).consumerDemand(goo));
    }
}
