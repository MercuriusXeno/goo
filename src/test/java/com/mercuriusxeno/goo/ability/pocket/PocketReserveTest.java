package com.mercuriusxeno.goo.ability.pocket;

import com.mercuriusxeno.goo.item.ContainerCapacity;
import net.minecraft.world.item.ItemStack;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** Covers the void Pocket reserves for what it holds, on the scale void grows a vat or canister. */
class PocketReserveTest {

    private static final long STACK = 64;

    @Nested
    class Scale {

        @ParameterizedTest
        @ValueSource(longs = {1, STACK, 1728, 100_000})
        void reservesTheLeastVoidWhoseVatGrowthHoldsTheItems(long items) {
            long reserved = PocketReserve.voidFor(items);
            long room = items * PocketReserve.MB_PER_ITEM;

            assertTrue(ContainerCapacity.voidGrowth(reserved) >= room,
                    "the void reserved should grow room for every item");
            assertTrue(ContainerCapacity.voidGrowth(reserved - 1) < room,
                    "one mB less should fall short, so the reserve is the least that holds them");
        }

        @Test
        void eachStackReservesLessThanTheStackBefore() {
            long first = PocketReserve.reserveToStore(0, STACK);
            long second = PocketReserve.reserveToStore(STACK, STACK);
            long third = PocketReserve.reserveToStore(2 * STACK, STACK);

            assertTrue(first > second && second > third,
                    "stacks should reserve " + first + " > " + second + " > " + third);
        }
    }

    @Nested
    class StoreAndTake {

        @Test
        void takingAStackBackReleasesWhatStoringItReserved() {
            long held = 500;
            long reserved = PocketReserve.reserveToStore(held, STACK);

            assertTrue(reserved > 0, "storing a stack should reserve void");
            assertEquals(reserved, PocketReserve.releaseOnTake(held + STACK, STACK));
        }

        @Test
        void theShelfHoldsBackTheReserveOfEveryItemOnIt() {
            List<PocketEntry> shelf = List.of(new PocketEntry(mock(ItemStack.class), STACK),
                    new PocketEntry(mock(ItemStack.class), 3));

            assertEquals(PocketReserve.voidFor(STACK + 3), PocketReserve.voidHeldBy(shelf));
        }

        @Test
        void storesAsMuchAsTheVoidAtHandReserves() {
            long shortOfAStack = PocketReserve.reserveToStore(0, STACK) - 1;

            long storable = PocketReserve.mostStorable(0, STACK, shortOfAStack);

            assertEquals(STACK - 1, storable);
        }
    }
}
