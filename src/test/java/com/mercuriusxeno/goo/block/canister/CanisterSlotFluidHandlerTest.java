package com.mercuriusxeno.goo.block.canister;

import com.mercuriusxeno.goo.item.StampedGoo;
import com.mercuriusxeno.goo.type.GooTypes;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A canister block slot takes a second goo type through its fluid handler while the
 * total stands under capacity, and an extract by resource removes that type alone
 * (decision canisters-hold-more-than-one-goo-type). StampedGoo stands the goo resources,
 * since the goo fluid is unregistered in the unit suite.
 */
class CanisterSlotFluidHandlerTest {

    private static final int CAPACITY = 1000;
    private static final int BLAZE_VOLUME = 400;
    private static final int ROCK_VOLUME = 250;

    private StampedGoo goo;
    private FluidResource blaze;
    private FluidResource rock;

    @BeforeEach
    void stampGooTypes() {
        goo = new StampedGoo();
        blaze = goo.resource(GooTypes.BLAZE);
        rock = goo.resource(GooTypes.ROCK);
    }

    @AfterEach
    void releaseGooFluids() {
        goo.close();
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
