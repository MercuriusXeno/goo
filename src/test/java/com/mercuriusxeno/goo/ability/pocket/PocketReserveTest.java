package com.mercuriusxeno.goo.ability.pocket;

import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Covers the void Pocket reserves: a flat amount for each slot its items fill, a stack's worth a slot. */
class PocketReserveTest {

    private static final int STACK = 64;
    private static final int SLOT_COST = 6;

    @Nested
    class Store {

        @Test
        void aFullStackFillsOneSlot() {
            assertEquals(SLOT_COST, PocketReserve.reserveToStore(0, STACK, STACK, SLOT_COST));
        }

        @Test
        void oneItemPastAStackFillsASecondSlot() {
            assertEquals(2 * SLOT_COST, PocketReserve.reserveToStore(0, STACK + 1, STACK, SLOT_COST));
        }

        @Test
        void topingUpAPartSlotReservesNothingMore() {
            assertEquals(0, PocketReserve.reserveToStore(10, 20, STACK, SLOT_COST));
        }

        @Test
        void eachUnstackableItemFillsItsOwnSlot() {
            assertEquals(3 * SLOT_COST, PocketReserve.reserveToStore(0, 3, 1, SLOT_COST));
        }

        @Test
        void storesAsMuchAsTheVoidAtHandReserves() {
            assertEquals(2 * STACK, PocketReserve.mostStorable(0, 3 * STACK, STACK, SLOT_COST, 2 * SLOT_COST + 1));
        }
    }

    @Nested
    class Take {

        @Test
        void takingAStackBackReleasesWhatStoringItReserved() {
            long held = 100;
            long reserved = PocketReserve.reserveToStore(held, STACK, STACK, SLOT_COST);

            assertEquals(reserved, PocketReserve.releaseOnTake(held + STACK, STACK, STACK, SLOT_COST));
        }
    }

    @Test
    void theShelfHoldsBackItsSlotsWorth() {
        ItemStack stone = mock(ItemStack.class);
        when(stone.getMaxStackSize()).thenReturn(STACK);
        ItemStack sword = mock(ItemStack.class);
        when(sword.getMaxStackSize()).thenReturn(1);
        List<PocketEntry> shelf = List.of(new PocketEntry(stone, 200), new PocketEntry(sword, 2));

        assertEquals((4 + 2) * SLOT_COST, PocketReserve.voidHeldBy(shelf, SLOT_COST));
    }
}
