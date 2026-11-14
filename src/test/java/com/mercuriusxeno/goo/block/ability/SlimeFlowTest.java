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
 * side neighbour standing at least two layers below it, and off an open
 * ledge before any neighbour, keeping its last layer, at a pace its goo
 * type's viscosity sets (decisions slime-is-a-layered-cell-that-levels-out,
 * spread-decreases-the-cell-it-leaves).
 */
class SlimeFlowTest {

    @Nested
    class Neighbours {

        @Test
        void levelNeighboursTakeNothing() {
            Optional<Direction> pour = SlimeFlow.pour(4, List.of(Side.holds(Direction.NORTH, 4),
                    Side.holds(Direction.EAST, 4)));

            assertEquals(Optional.empty(), pour);
        }

        @Test
        void aNeighbourOneLayerLowerTakesNothing() {
            Optional<Direction> pour = SlimeFlow.pour(4, List.of(Side.holds(Direction.EAST, 3)));

            assertEquals(Optional.empty(), pour);
        }

        @Test
        void aNeighbourTwoLayersLowerTakesALayer() {
            Optional<Direction> pour = SlimeFlow.pour(4, List.of(Side.holds(Direction.NORTH, 4),
                    Side.holds(Direction.EAST, 2)));

            assertEquals(Optional.of(Direction.EAST), pour);
        }

        @Test
        void theLowestNeighbourTakesTheLayer() {
            Optional<Direction> pour = SlimeFlow.pour(8, List.of(Side.holds(Direction.NORTH, 5),
                    Side.holds(Direction.EAST, 0), Side.holds(Direction.SOUTH, 3)));

            assertEquals(Optional.of(Direction.EAST), pour);
        }

        @Test
        void aBlockedSideTakesNothing() {
            Optional<Direction> pour = SlimeFlow.pour(8, List.of(Side.blocked(Direction.WEST)));

            assertEquals(Optional.empty(), pour);
        }
    }

    @Nested
    class Ledges {

        @Test
        void anOpenLedgeTakesALayer() {
            Optional<Direction> pour = SlimeFlow.pour(2, List.of(Side.ledge(Direction.SOUTH)));

            assertEquals(Optional.of(Direction.SOUTH), pour);
        }

        @Test
        void aLedgeWinsOverALowerNeighbour() {
            Optional<Direction> pour = SlimeFlow.pour(8, List.of(Side.holds(Direction.NORTH, 0),
                    Side.ledge(Direction.WEST)));

            assertEquals(Optional.of(Direction.WEST), pour);
        }

        @Test
        void aOneLayerCellKeepsItsLayerAtALedge() {
            Optional<Direction> pour = SlimeFlow.pour(1, List.of(Side.ledge(Direction.SOUTH)));

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
