package com.mercuriusxeno.goo.block.hub;

import com.mercuriusxeno.goo.block.canister.CanisterSlotFluidHandler;
import com.mercuriusxeno.goo.block.canister.SlottedCanisterData;
import com.mercuriusxeno.goo.block.gasket.GasketDemand;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import java.util.OptionalInt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A hub's intake asks what its canisters ask together (decision
 * receivers-demand-and-links-relay): each canister that takes the fluid adds its own
 * demand, and one that refuses it adds nothing. The hub and its canisters are mocks;
 * a block entity class initializes only past NeoForge's AttachmentHolder and the
 * vanilla registries, so a stubbed FML loader and the bootstrap stand those.
 */
class HubFluidHandlerDemandTest {

    private static final int FIRST_DEMAND = 4_000;
    private static final int SECOND_DEMAND = 690;
    private static final int REFUSING_DEMAND = 1_000_000;
    private static final int ARRIVING = 10_000;

    /**
     * @throws ClassNotFoundException never, the class is on the test classpath
     */
    @BeforeAll
    static void initializeBlockEntitySupertypes() throws ClassNotFoundException {
        try (MockedStatic<FMLLoader> loader = mockStatic(FMLLoader.class, RETURNS_DEEP_STUBS)) {
            Class.forName(AttachmentHolder.class.getName(), true, AttachmentHolder.class.getClassLoader());
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
        }
    }

    private static CanisterSlotFluidHandler canisterAsking(int demand, boolean takes, FluidResource resource) {
        CanisterSlotFluidHandler canister = mock(CanisterSlotFluidHandler.class);
        when(canister.isValid(0, resource)).thenReturn(takes);
        when(canister.statedDemand(resource)).thenReturn(OptionalInt.of(demand));
        return canister;
    }

    @Test
    void theIntakeAsksTheSumOfTheCanistersTakingTheFluid() {
        FluidResource goo = mock(FluidResource.class);
        HubBlockEntity hub = mock(HubBlockEntity.class);
        SlottedCanisterData slots = mock(SlottedCanisterData.class);
        when(hub.containerState()).thenReturn(slots);
        CanisterSlotFluidHandler first = canisterAsking(FIRST_DEMAND, true, goo);
        CanisterSlotFluidHandler second = canisterAsking(SECOND_DEMAND, true, goo);
        CanisterSlotFluidHandler refusing = canisterAsking(REFUSING_DEMAND, false, goo);
        when(slots.getSlotFluidHandler(0)).thenReturn(first);
        when(slots.getSlotFluidHandler(3)).thenReturn(second);
        when(slots.getSlotFluidHandler(5)).thenReturn(refusing);
        assertEquals(FIRST_DEMAND + SECOND_DEMAND, GasketDemand.demandOf(new HubFluidHandler(hub), goo));
    }

    /**
     * Flow arriving at the intake gives each canister taking the fluid up to the demand it states, so the resting
     * canister takes its own demand and the rest passes on (decision relay-adds-dependent-ask-to-own).
     */
    @Test
    void arrivingFlowGivesEachTakingCanisterUpToItsOwnDemand() {
        FluidResource goo = mock(FluidResource.class);
        HubBlockEntity hub = mock(HubBlockEntity.class);
        SlottedCanisterData slots = mock(SlottedCanisterData.class);
        when(hub.containerState()).thenReturn(slots);
        CanisterSlotFluidHandler resting = canisterAsking(SECOND_DEMAND, true, goo);
        CanisterSlotFluidHandler feeding = canisterAsking(FIRST_DEMAND, true, goo);
        when(resting.insert(eq(0), eq(goo), anyInt(), any())).thenAnswer(call -> call.getArgument(2));
        when(feeding.insert(eq(0), eq(goo), anyInt(), any())).thenAnswer(call -> call.getArgument(2));
        when(slots.getSlotFluidHandler(0)).thenReturn(resting);
        when(slots.getSlotFluidHandler(3)).thenReturn(feeding);
        // TransactionContext is sealed and every handler under the hub is a mock, so no transaction is opened.
        TransactionContext transaction = null;

        new HubFluidHandler(hub).insert(0, goo, ARRIVING, transaction);

        verify(resting).insert(0, goo, SECOND_DEMAND, transaction);
        verify(feeding).insert(0, goo, FIRST_DEMAND, transaction);
    }
}