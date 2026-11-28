package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers what Level's disc does with the Mortar it carries: each gap under the
 * disc takes a stone block for a layer, and the layers left lie back over the
 * disc in whole cells of fifteen; the disc scoops no more than nine piles of fifteen.
 * level-dragged-under-rock-goo-carries-it-and-deposits-stone-under-the-disc
 * level-scoops-up-to-three-by-three-piles-of-fifteen-layers
 * layer-of-rock-goo-is-a-stone-block
 */
class MortarPavingTest {

    private static final BlockPos FIRST = new BlockPos(0, 0, 0);
    private static final BlockPos SECOND = new BlockPos(1, 0, 0);
    private static final BlockPos THIRD = new BlockPos(2, 0, 0);
    private static final int TEN_LAYERS = 10;
    private static final int SEVEN_LAYERS = 7;
    private static final int TWO_LAYERS = 2;
    private static final int FULL = 15;
    private static final int TWENTY_LAYERS = 20;

    @Nested
    class Deposit {

        @Test
        void eachGapTakesAStoneForALayer() {
            MortarPaving.Deposit deposit = MortarPaving.deposit(List.of(FIRST, SECOND, THIRD), TEN_LAYERS);
            assertEquals(List.of(FIRST, SECOND, THIRD), deposit.paved());
            assertEquals(SEVEN_LAYERS, deposit.layersLeft());
        }

        @Test
        void theCarrySpentLeavesTheLastGapsOpen() {
            MortarPaving.Deposit deposit = MortarPaving.deposit(List.of(FIRST, SECOND, THIRD), TWO_LAYERS);
            assertEquals(List.of(FIRST, SECOND), deposit.paved());
            assertEquals(0, deposit.layersLeft());
        }
    }

    @Nested
    class Scoop {

        private static final int GRID = 5;
        private static final int MOST_CARRIED = 135;
        private static final int PILES_CARRIED = 9;
        private static final int PILES_LEFT = 16;
        private static final int CARRIED_IN = 130;
        private static final int ROOM_LEFT = 5;

        @Test
        void aFiveByFiveOfFullCellsGivesUpNinePilesOfFifteen() {
            Map<BlockPos, Integer> piles = new LinkedHashMap<>();
            for (int x = 0; x < GRID; x++) {
                for (int z = 0; z < GRID; z++) {
                    piles.put(new BlockPos(x, 0, z), FULL);
                }
            }
            MortarPaving.Scoop scoop = MortarPaving.scoop(piles, 0);
            assertEquals(MOST_CARRIED, scoop.pickedUp().values().stream().mapToInt(Integer::intValue).sum());
            assertEquals(PILES_CARRIED, scoop.pickedUp().size());
            assertEquals(PILES_LEFT, scoop.leftOut().size());
            assertTrue(scoop.leftOut().values().stream().allMatch(layers -> layers == FULL));
        }

        @Test
        void aNearlyFullCarryTakesOnlyThePartOfAPileItHasRoomFor() {
            MortarPaving.Scoop scoop = MortarPaving.scoop(Map.of(FIRST, FULL), CARRIED_IN);
            assertEquals(Map.of(FIRST, ROOM_LEFT), scoop.pickedUp());
            assertEquals(Map.of(FIRST, FULL - ROOM_LEFT), scoop.leftOut());
        }
    }

    @Nested
    class Lay {

        @Test
        void theLayersLeftFillTheFirstCellsWholeFirst() {
            assertEquals(Map.of(FIRST, FULL, SECOND, TWENTY_LAYERS - FULL),
                    MortarPaving.lay(List.of(FIRST, SECOND, THIRD), TWENTY_LAYERS));
        }

        @Test
        void noLayersLayNoCell() {
            assertEquals(Map.of(), MortarPaving.lay(List.of(FIRST, SECOND), 0));
        }
    }
}
