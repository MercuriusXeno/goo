package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers what Level's disc does with the Mortar it carries: each gap under the
 * disc takes a stone block for a layer, and the layers left lie back over the
 * disc in whole cells of fifteen; the disc scoops no more than nine piles of
 * fifteen, each scooped layer climbing onto the closest pile with room.
 * level-dragged-under-rock-goo-carries-it-and-deposits-stone-under-the-disc
 * level-scoops-up-to-three-by-three-piles-of-fifteen-layers
 * level-gathers-loose-mortar-into-the-closest-empty-slot
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
        private static final int ROOM_LEFT = 5;
        private static final int TWELVE = 12;
        private static final int FOUR = 4;
        /** The disc's middle, the 3x3 of pile columns standing at x and z 1 to 3 just over it. */
        private static final BlockPos DISC = new BlockPos(2, -1, 2);
        private static final ChannelAim.FacePlane PLANE = new ChannelAim.FacePlane(DISC, Direction.UP);
        private static final List<BlockPos> PILE_CELLS = MortarPaving.pileCells(PLANE, DISC);
        private static final int MIDDLE_PILE = 0;
        /** The corner pile the ring reaches first, at the ring's lowest x and z. */
        private static final int FIRST_CORNER_PILE = 1;
        /** The first edge pile in the ring's order, the nearest piles to the middle after it. */
        private static final int FIRST_EDGE_PILE = 2;
        private static final int LAST_PILE = 8;

        private static List<Integer> carry(int pile, int layers) {
            List<Integer> piles = new ArrayList<>(Collections.nCopies(PILES_CARRIED, 0));
            piles.set(pile, layers);
            return piles;
        }

        @Test
        void aFiveByFiveOfFullCellsGivesUpNinePilesOfFifteen() {
            Map<BlockPos, Integer> loose = new LinkedHashMap<>();
            for (int x = 0; x < GRID; x++) {
                for (int z = 0; z < GRID; z++) {
                    loose.put(new BlockPos(x, 0, z), FULL);
                }
            }
            MortarPaving.Scoop scoop = MortarPaving.scoop(carry(MIDDLE_PILE, 0), PILE_CELLS, loose);
            assertEquals(MOST_CARRIED, scoop.pickedUp().values().stream().mapToInt(Integer::intValue).sum());
            assertEquals(PILES_CARRIED, scoop.pickedUp().size());
            assertEquals(PILES_LEFT, scoop.leftOut().size());
            assertTrue(scoop.leftOut().values().stream().allMatch(layers -> layers == FULL));
        }

        @Test
        void aNearlyFullCarryTakesOnlyThePartOfAPileItHasRoomFor() {
            List<Integer> nearlyFull = new ArrayList<>(Collections.nCopies(PILES_CARRIED, FULL));
            nearlyFull.set(LAST_PILE, FULL - ROOM_LEFT);
            MortarPaving.Scoop scoop = MortarPaving.scoop(nearlyFull, PILE_CELLS, Map.of(FIRST, FULL));
            assertEquals(Map.of(FIRST, ROOM_LEFT), scoop.pickedUp());
            assertEquals(Map.of(FIRST, FULL - ROOM_LEFT), scoop.leftOut());
        }

        @Test
        void aTwelveLayerCornerPileUnderALooseLayerClimbsToThirteen() {
            BlockPos inTheCornerColumn = PILE_CELLS.get(FIRST_CORNER_PILE);
            MortarPaving.Scoop scoop = MortarPaving.scoop(carry(FIRST_CORNER_PILE, TWELVE), PILE_CELLS,
                    Map.of(inTheCornerColumn, 1));
            assertEquals(TWELVE + 1, scoop.piles().get(FIRST_CORNER_PILE));
            assertEquals(0, scoop.piles().get(MIDDLE_PILE));
            assertEquals(Map.of(inTheCornerColumn, 1), scoop.pickedUp());
            assertEquals(Map.of(), scoop.leftOut());
        }

        @Test
        void aLooseLayerOverACornerColumnClimbsOntoTheCornerPile() {
            MortarPaving.Scoop scoop = MortarPaving.scoop(carry(MIDDLE_PILE, 0), PILE_CELLS,
                    Map.of(PILE_CELLS.get(FIRST_CORNER_PILE), 1));
            assertEquals(carry(FIRST_CORNER_PILE, 1), scoop.piles());
        }

        @Test
        void aLooseLayerOverAFullMiddleClimbsOntoTheFirstNearestRingPile() {
            MortarPaving.Scoop scoop = MortarPaving.scoop(carry(MIDDLE_PILE, FULL), PILE_CELLS,
                    Map.of(PILE_CELLS.get(MIDDLE_PILE), 1));
            List<Integer> expected = carry(MIDDLE_PILE, FULL);
            expected.set(FIRST_EDGE_PILE, 1);
            assertEquals(expected, scoop.piles());
        }

        @Test
        void aFullCarryLeavesLooseMortarWhereItLies() {
            MortarPaving.Scoop scoop = MortarPaving.scoop(Collections.nCopies(PILES_CARRIED, FULL), PILE_CELLS,
                    Map.of(FIRST, FOUR));
            assertEquals(Map.of(), scoop.pickedUp());
            assertEquals(Map.of(FIRST, FOUR), scoop.leftOut());
        }
    }

    @Nested
    class SoundSpot {

        private static final double CENTER = 0.5;

        @Test
        void threeBlocksInARowSoundAtTheMiddleBlocksCenter() {
            assertEquals(Optional.of(new Vec3(SECOND.getX() + CENTER, CENTER, CENTER)),
                    MortarPaving.soundSpot(List.of(FIRST, SECOND, THIRD)));
        }

        @Test
        void oneBlockSoundsAtItsOwnCenter() {
            assertEquals(Optional.of(Vec3.atCenterOf(THIRD)), MortarPaving.soundSpot(List.of(THIRD)));
        }

        @Test
        void noBlocksLaidSoundNowhere() {
            assertEquals(Optional.empty(), MortarPaving.soundSpot(List.of()));
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
