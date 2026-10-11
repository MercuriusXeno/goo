package com.mercuriusxeno.goo.block.canister;

import com.mercuriusxeno.goo.registry.GooFluids;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * A canister block slot takes a second goo type through its fluid handler while the
 * total stands under capacity, and an extract by resource removes that type alone
 * (decision canisters-hold-more-than-one-goo-type). Goo resources are mocks whose type
 * GooFluids answers through a static mock, since the goo fluid is unregistered in the
 * unit suite.
 */
class CanisterSlotFluidHandlerTest {

    private static final int CAPACITY = 1000;
    private static final int BLAZE_VOLUME = 400;
    private static final int ROCK_VOLUME = 250;

    private final FluidResource blaze = mock(FluidResource.class);
    private final FluidResource rock = mock(FluidResource.class);
    private MockedStatic<GooFluids> gooFluids;

    @BeforeAll
    static void standVanillaFluids() {
        try (MockedStatic<FMLLoader> loader = mockStatic(FMLLoader.class, RETURNS_DEEP_STUBS)) {
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
        }
    }

    @BeforeEach
    void stampGooTypes() {
        Map<FluidResource, ResourceKey<GooTypeDefinition>> types = Map.of(blaze, GooTypes.BLAZE, rock, GooTypes.ROCK);
        gooFluids = mockStatic(GooFluids.class);
        gooFluids.when(() -> GooFluids.keyOf(any())).thenAnswer(call -> types.get(call.<FluidResource>getArgument(0)));
    }

    @AfterEach
    void releaseGooFluids() {
        gooFluids.close();
    }

    @Test
    void aSlotHoldingOneTypeTakesASecondAndGivesUpEachAlone() {
        CanisterSlotFluidHandler slot = new CanisterSlotFluidHandler(CAPACITY, () -> { });
        slot.insertFluid(blaze, BLAZE_VOLUME, false);

        assertTrue(slot.isValid(0, rock));
        assertEquals(ROCK_VOLUME, slot.insertFluid(rock, ROCK_VOLUME, false));
        assertEquals(Map.of(GooTypes.BLAZE, BLAZE_VOLUME, GooTypes.ROCK, ROCK_VOLUME), slot.toGooContents().getAll());

        assertEquals(ROCK_VOLUME, slot.extractFluid(rock, CAPACITY, false));
        assertEquals(BLAZE_VOLUME, slot.toFluidContent().volumeOf(blaze));
        assertEquals(0, slot.toFluidContent().volumeOf(rock));
    }

    @Test
    void theSecondTypeStopsAtTheCapacityLessTheFirst() {
        CanisterSlotFluidHandler slot = new CanisterSlotFluidHandler(CAPACITY, () -> { });
        slot.insertFluid(blaze, BLAZE_VOLUME, false);
        assertEquals(CAPACITY - BLAZE_VOLUME, slot.insertFluid(rock, CAPACITY, false));
    }

    @Test
    void aSimulatedInsertLeavesTheSlotAsItStood() {
        CanisterSlotFluidHandler slot = new CanisterSlotFluidHandler(CAPACITY, () -> { });
        slot.insertFluid(blaze, BLAZE_VOLUME, false);
        slot.insertFluid(rock, ROCK_VOLUME, true);
        assertEquals(BLAZE_VOLUME, slot.totalVolume());
    }
}
