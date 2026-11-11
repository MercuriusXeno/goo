package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.block.ability.SlimeFlow.Side;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The slime pour rule, pure: a cell pours one layer a tick into its lowest
 * side neighbour standing at least two layers below it, and off an open
 * ledge before any neighbour, keeping its last layer
 * (decision slime-is-a-layered-cell-that-levels-out).
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
}
