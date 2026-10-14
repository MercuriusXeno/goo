package com.mercuriusxeno.goo.block.canister;

import com.mercuriusxeno.goo.item.ContainerCapacity;
import com.mercuriusxeno.goo.item.StampedGoo;
import com.mercuriusxeno.goo.type.GooTypes;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
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
    /** The void that first grows a canister to a vat's base capacity. */
    private static final int CANISTER_TO_VAT_VOID = 8_062;

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

    /**
     * Infinity never leaves the slot, while the blaze beside it does.
     * decision infinity-sources-whatever-shares-its-vessel
     */
    @Test
    void anExtractTakesNoInfinity() {
        FluidResource infinity = goo.resource(GooTypes.INFINITY);
        CanisterSlotFluidHandler slot = new CanisterSlotFluidHandler(CAPACITY, () -> { });
        slot.insertFluid(infinity, 1, false);
        slot.insertFluid(blaze, BLAZE_VOLUME, false);

        assertEquals(0, slot.extractFluid(infinity, CAPACITY, false));
        assertEquals(BLAZE_VOLUME, slot.extractFluid(blaze, CAPACITY, false));
        assertEquals(Map.of(GooTypes.INFINITY, 1), slot.toGooContents().getAll());
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

    /**
     * Void a canister slot holds as ordinary goo grows its capacity, and a void
     * drain stops where the rest would overflow (decision
     * each-void-mb-adds-n-plus-one-capacity).
     */
    @Nested
    class VoidGrowsCapacity {

        private CanisterSlotFluidHandler slot;
        private FluidResource voidGoo;

        @BeforeEach
        void openACanisterSlot() {
            slot = new CanisterSlotFluidHandler(ContainerCapacity.CANISTER_BASE, () -> { });
            voidGoo = goo.resource(GooTypes.VOID);
        }

        @Test
        void voidGrowsACanisterToAVatsBase() {
            assertEquals(CANISTER_TO_VAT_VOID, slot.insertFluid(voidGoo, CANISTER_TO_VAT_VOID, false));

            assertEquals(ContainerCapacity.canisterCapacity(CANISTER_TO_VAT_VOID), slot.capacity());
            assertTrue(slot.capacity() >= ContainerCapacity.VAT_BASE);
            assertEquals(slot.capacity() - CANISTER_TO_VAT_VOID, slot.insertFluid(rock, ContainerCapacity.VAT_BASE, false));
        }

        /**
         * Over 1,000 mB past its base of rock, the slot gives up void only down to
         * 45 mB, the least whose growth covers that overage.
         */
        @Test
        void aVoidDrainTakesOnlyWhatTheRemainingCapacitySpares() {
            slot.insertFluid(voidGoo, CANISTER_TO_VAT_VOID, false);
            slot.insertFluid(rock, ContainerCapacity.CANISTER_BASE + 1_000, false);

            assertEquals(CANISTER_TO_VAT_VOID - 45, slot.extractFluid(voidGoo, CANISTER_TO_VAT_VOID, false));
            assertEquals(45, slot.toFluidContent().volumeOf(voidGoo));
            assertTrue(slot.totalVolume() <= slot.capacity());
        }

        @Test
        void anOtherwiseEmptySlotDrainsItsVoidWhole() {
            slot.insertFluid(voidGoo, CANISTER_TO_VAT_VOID, false);

            assertEquals(CANISTER_TO_VAT_VOID, slot.extractFluid(voidGoo, CANISTER_TO_VAT_VOID, false));
            assertEquals(ContainerCapacity.CANISTER_BASE, slot.capacity());
        }
    }
}
