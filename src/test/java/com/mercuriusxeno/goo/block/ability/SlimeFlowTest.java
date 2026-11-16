package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.block.ability.SlimeFlow.Side;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The slime pour rule, pure: a cell pours one whole layer into its lowest
 * side neighbour standing at least two layers plus its goo's cohesion below
 * it, and off an open ledge before any neighbour, keeping its last layer, at
 * a pace its goo type's viscosity sets (decisions
 * slime-is-a-layered-cell-that-levels-out, spread-decreases-the-cell-it-leaves,
 * cohesion-is-the-step-the-goo-holds).
 */
class SlimeFlowTest {

    /** The cohesion of a goo that levels as flat as whole layers allow. */
    private static final int RUNNY = 0;

    /**
     * A cell pours only the layers standing above its goo's cohesion step
     * (decision cohesion-is-the-step-the-goo-holds).
     */
    @Nested
    class Cohesion {

        private static final int GOOP_COHESION = 4;
        private static final int FIRM = 15;
        private static final int CELL = 10;

        @Test
        void aRunnyCellPoursIntoASideTwoLayersBelow() {
            assertEquals(Optional.of(Direction.EAST),
                    SlimeFlow.pour(CELL, RUNNY, List.of(Side.holds(Direction.EAST, CELL - 2))));
        }

        @Test
        void aCohesiveCellHoldsItsStepOverASideFiveLayersBelow() {
            assertEquals(Optional.empty(),
                    SlimeFlow.pour(CELL, GOOP_COHESION, List.of(Side.holds(Direction.EAST, CELL - 5))));
        }

        @Test
        void aCohesiveCellPoursIntoASideSixLayersBelow() {
            assertEquals(Optional.of(Direction.EAST),
                    SlimeFlow.pour(CELL, GOOP_COHESION, List.of(Side.holds(Direction.EAST, CELL - 6))));
        }

        @Test
        void aFirmCellNeverPours() {
            assertEquals(Optional.empty(), SlimeFlow.pour(VolumeCellBlock.MAX_LAYERS, FIRM,
                    List.of(Side.holds(Direction.EAST, 0), Side.ledge(Direction.WEST))));
        }
    }

    @Nested
    class Neighbours {

        @Test
        void levelNeighboursTakeNothing() {
            Optional<Direction> pour = SlimeFlow.pour(4, RUNNY, List.of(Side.holds(Direction.NORTH, 4),
                    Side.holds(Direction.EAST, 4)));

            assertEquals(Optional.empty(), pour);
        }

        @Test
        void aNeighbourOneLayerLowerTakesNothing() {
            Optional<Direction> pour = SlimeFlow.pour(4, RUNNY, List.of(Side.holds(Direction.EAST, 3)));

            assertEquals(Optional.empty(), pour);
        }

        @Test
        void aNeighbourTwoLayersLowerTakesALayer() {
            Optional<Direction> pour = SlimeFlow.pour(4, RUNNY, List.of(Side.holds(Direction.NORTH, 4),
                    Side.holds(Direction.EAST, 2)));

            assertEquals(Optional.of(Direction.EAST), pour);
        }

        @Test
        void theLowestNeighbourTakesTheLayer() {
            Optional<Direction> pour = SlimeFlow.pour(8, RUNNY, List.of(Side.holds(Direction.NORTH, 5),
                    Side.holds(Direction.EAST, 0), Side.holds(Direction.SOUTH, 3)));

            assertEquals(Optional.of(Direction.EAST), pour);
        }

        @Test
        void aBlockedSideTakesNothing() {
            Optional<Direction> pour = SlimeFlow.pour(8, RUNNY, List.of(Side.blocked(Direction.WEST)));

            assertEquals(Optional.empty(), pour);
        }
    }

    @Nested
    class Ledges {

        @Test
        void anOpenLedgeTakesALayer() {
            Optional<Direction> pour = SlimeFlow.pour(2, RUNNY, List.of(Side.ledge(Direction.SOUTH)));

            assertEquals(Optional.of(Direction.SOUTH), pour);
        }

        @Test
        void aLedgeWinsOverALowerNeighbour() {
            Optional<Direction> pour = SlimeFlow.pour(8, RUNNY, List.of(Side.holds(Direction.NORTH, 0),
                    Side.ledge(Direction.WEST)));

            assertEquals(Optional.of(Direction.WEST), pour);
        }

        @Test
        void aOneLayerCellKeepsItsLayerAtALedge() {
            Optional<Direction> pour = SlimeFlow.pour(1, RUNNY, List.of(Side.ledge(Direction.SOUTH)));

            assertEquals(Optional.empty(), pour);
        }
    }

    /**
     * A pour moves one whole layer: the giver loses it and the taker gains it
     * (decision spread-decreases-the-cell-it-leaves).
     */
    @Nested
    class OneLayerMoves {

        private final SlimeFlow.Poured poured = SlimeFlow.poured(9, 4);

        @Test
        void theGiverLosesOneLayer() {
            assertEquals(8, poured.giver());
        }

        @Test
        void theTakerGainsOneLayer() {
            assertEquals(5, poured.taker());
        }

        @Test
        void theLayersBetweenThemStayTheSame() {
            assertEquals(9 + 4, poured.giver() + poured.taker());
        }
    }

    /**
     * The ticks between a cell's pours follow its goo type's viscosity, on the
     * scale where water is 1000 and higher is thicker, so a thinner goo pours
     * sooner (decision spread-decreases-the-cell-it-leaves).
     */
    @Nested
    class PourPace {

        private static final int KINETIC_VISCOSITY = 800;
        private static final int WEIRD_VISCOSITY = 1000;

        @Test
        void aThinnerGooWaitsFewerTicksBetweenPours() {
            assertTrue(SlimeFlow.pourTicks(KINETIC_VISCOSITY) < SlimeFlow.pourTicks(WEIRD_VISCOSITY),
                    "viscosity 800 should pour sooner than 1000");
        }

        @Test
        void aGooWithNoViscosityStillWaitsATick() {
            assertEquals(1, SlimeFlow.pourTicks(0));
        }
    }
}
