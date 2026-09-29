package com.mercuriusxeno.goo.block.crystallizer;

import com.mercuriusxeno.goo.block.canister.CanisterSlotLayout;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Where the crystallizer's two canisters stand in the canister block on its top
 * (operator ruling: back left and back right at model (4, 4) and (12, 4), off the
 * canister grid), turned to each facing; plain values, no registry.
 */
class CrystallizerLayoutTest {

    private static final int BACK_LEFT = 0;
    private static final int BACK_RIGHT = 1;

    @Nested
    class SlotsPerFacing {

        @Test
        void southKeepsTheModelsBackCorners() {
            assertSlots(Direction.SOUTH, 0, 2);
        }

        @Test
        void westTurnsTheBackToTheEast() {
            assertSlots(Direction.WEST, 2, 8);
        }

        @Test
        void northTurnsTheBackToTheSouth() {
            assertSlots(Direction.NORTH, 8, 6);
        }

        @Test
        void eastTurnsTheBackToTheWest() {
            assertSlots(Direction.EAST, 6, 0);
        }

        private void assertSlots(Direction facing, int backLeft, int backRight) {
            assertEquals(backLeft, CrystallizerLayout.slot(facing, BACK_LEFT), "back left");
            assertEquals(backRight, CrystallizerLayout.slot(facing, BACK_RIGHT), "back right");
            assertEquals(Set.of(backLeft, backRight), CrystallizerLayout.allowedSlots(facing));
        }
    }

    @Nested
    class CentersPerFacing {

        @Test
        void northMovesTheTwoSlotsToTheTurnedModelCenters() {
            float[][] centers = CrystallizerLayout.centers(Direction.NORTH);
            assertArrayEquals(new float[] {12, 12}, centers[CrystallizerLayout.slot(Direction.NORTH, BACK_LEFT)]);
            assertArrayEquals(new float[] {4, 12}, centers[CrystallizerLayout.slot(Direction.NORTH, BACK_RIGHT)]);
        }

        @Test
        void eastMovesTheTwoSlotsToTheTurnedModelCenters() {
            float[][] centers = CrystallizerLayout.centers(Direction.EAST);
            assertArrayEquals(new float[] {4, 12}, centers[CrystallizerLayout.slot(Direction.EAST, BACK_LEFT)]);
            assertArrayEquals(new float[] {4, 4}, centers[CrystallizerLayout.slot(Direction.EAST, BACK_RIGHT)]);
        }

        @ParameterizedTest
        @EnumSource(value = Direction.class, names = {"NORTH", "SOUTH", "EAST", "WEST"})
        void everyOtherSlotKeepsTheGrid(Direction facing) {
            float[][] centers = CrystallizerLayout.centers(facing);
            Set<Integer> moved = CrystallizerLayout.allowedSlots(facing);
            for (int slot = 0; slot < CanisterSlotLayout.SLOT_COUNT; slot++) {
                if (!moved.contains(slot)) {
                    assertArrayEquals(CanisterSlotLayout.SLOT_CENTERS[slot], centers[slot], "slot " + slot);
                }
            }
        }

        @ParameterizedTest
        @EnumSource(value = Direction.class, names = {"NORTH", "SOUTH", "EAST", "WEST"})
        void eachFacingHandsBackOneStableArray(Direction facing) {
            assertSame(CrystallizerLayout.centers(facing), CrystallizerLayout.centers(facing));
        }

        @ParameterizedTest
        @EnumSource(value = Direction.class, names = {"NORTH", "SOUTH", "EAST", "WEST"})
        void aHitAtATurnedCenterAddressesItsSlot(Direction facing) {
            float[][] centers = CrystallizerLayout.centers(facing);
            for (int role : new int[] {BACK_LEFT, BACK_RIGHT}) {
                int slot = CrystallizerLayout.slot(facing, role);
                assertEquals(slot, CanisterSlotLayout.nearestSlot(centers, centers[slot][0], centers[slot][1]));
            }
        }
    }
}
