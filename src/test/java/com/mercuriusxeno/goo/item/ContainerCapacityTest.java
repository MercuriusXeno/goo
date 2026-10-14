package com.mercuriusxeno.goo.item;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A vat or canister's capacity is its base plus v(v+3)/2 for v mB of held void,
 * clamped at Integer.MAX_VALUE; void fits until the total reaches that clamp, and
 * a void drain stops where the rest would overflow (decisions
 * void-goo-grows-a-container and each-void-mb-adds-n-plus-one-capacity).
 */
class ContainerCapacityTest {

    /** The void that first grows a canister to a vat's base capacity. */
    private static final int CANISTER_TO_VAT_VOID = 8_062;

    @Nested
    class Growth {

        /**
         * The n-th mB of void adds n+1: 1 adds 2, 2 add 5, 3 add 9.
         *
         * @param voidHeld the void held
         * @param growth   the capacity it adds
         */
        @ParameterizedTest(name = "{0} mB of void adds {1}")
        @CsvSource({"0, 0", "1, 2", "2, 5", "3, 9", "4, 14", "10, 65"})
        void eachMbOfVoidAddsOneMoreThanItsOrdinal(int voidHeld, long growth) {
            assertEquals(growth, ContainerCapacity.voidGrowth(voidHeld));
            assertEquals(ContainerCapacity.CANISTER_BASE + growth, ContainerCapacity.canisterCapacity(voidHeld));
            assertEquals(ContainerCapacity.VAT_BASE + growth, ContainerCapacity.vatCapacity(voidHeld));
        }

        /** A vessel holding no void holds exactly its base. */
        @Test
        void noVoidLeavesTheBase() {
            assertEquals(1 << 20, ContainerCapacity.canisterCapacity(0));
            assertEquals(1 << 25, ContainerCapacity.vatCapacity(0));
        }

        /** 8,062 mB of void is the least that gives a canister a vat's base capacity. */
        @Test
        void eightThousandSixtyTwoVoidGivesACanisterAVatsBase() {
            assertTrue(ContainerCapacity.canisterCapacity(CANISTER_TO_VAT_VOID) >= ContainerCapacity.VAT_BASE);
            assertTrue(ContainerCapacity.canisterCapacity(CANISTER_TO_VAT_VOID - 1) < ContainerCapacity.VAT_BASE);
            assertEquals(CANISTER_TO_VAT_VOID,
                    ContainerCapacity.voidToReach(ContainerCapacity.CANISTER_BASE, ContainerCapacity.VAT_BASE));
        }

        /** The growth is figured in long, so void past int overflow clamps rather than wrapping. */
        @Test
        void capacityClampsAtIntegerMaxValue() {
            assertEquals(Integer.MAX_VALUE, ContainerCapacity.vatCapacity(65_536));
            assertEquals(Integer.MAX_VALUE, ContainerCapacity.canisterCapacity(Integer.MAX_VALUE));
            assertTrue(ContainerCapacity.canisterCapacity(65_000) < Integer.MAX_VALUE);
        }
    }

    @Nested
    class Room {

        /** Goo other than void fills the capacity the held void sets. */
        @Test
        void otherGooFillsTheCapacityTheVoidSets() {
            assertEquals(1_000 + 9 - 400, ContainerCapacity.room(false, 1_000, 400, 3));
            assertEquals(0, ContainerCapacity.room(false, 1_000, 2_000, 0));
        }

        /** Void outgrows the room it takes, so it fits until the total reaches the int clamp. */
        @Test
        void voidFitsUpToTheIntClamp() {
            assertEquals(Integer.MAX_VALUE - 1_000, ContainerCapacity.room(true, 1_000, 1_000, 0));
        }
    }

    @Nested
    class VoidDrain {

        /**
         * A canister holding 8,062 mB of void and 1,000 mB past its base of other
         * goo drains void only down to 45 mB, the least whose growth, 45*46/2 past
         * its own volume, still covers the 1,000 mB the rest stands over the base.
         */
        @Test
        void aDrainStopsWhereTheRestWouldOverflow() {
            int base = ContainerCapacity.CANISTER_BASE;
            long total = CANISTER_TO_VAT_VOID + base + 1_000L;

            assertEquals(CANISTER_TO_VAT_VOID - 45,
                    ContainerCapacity.releasableVoid(base, total, CANISTER_TO_VAT_VOID));
        }

        /** An otherwise empty vessel drains its void whole. */
        @Test
        void anOtherwiseEmptyVesselDrainsItsVoidWhole() {
            assertEquals(CANISTER_TO_VAT_VOID, ContainerCapacity.releasableVoid(
                    ContainerCapacity.CANISTER_BASE, CANISTER_TO_VAT_VOID, CANISTER_TO_VAT_VOID));
        }

        /** A vessel clamped at the int ceiling drains the void it holds past what the clamp needs. */
        @Test
        void aClampedVesselDrainsTheVoidPastTheClamp() {
            int voidHeld = 70_000;
            long total = Integer.MAX_VALUE;
            int released = ContainerCapacity.releasableVoid(0, total, voidHeld);

            assertTrue(total - released <= ContainerCapacity.capacity(0, voidHeld - released));
            assertTrue(total - released - 1 > ContainerCapacity.capacity(0, voidHeld - released - 1));
        }

        /** A vessel already over capacity releases no void. */
        @Test
        void anOverfullVesselReleasesNoVoid() {
            assertEquals(0, ContainerCapacity.releasableVoid(1_000, 2_000, 3));
        }
    }
}
