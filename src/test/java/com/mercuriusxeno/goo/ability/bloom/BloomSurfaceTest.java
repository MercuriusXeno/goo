package com.mercuriusxeno.goo.ability.bloom;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The surface a cell offers Bloom's plants: water, a cave's floor, ceiling or
 * wall, open plantable ground, or a wall under the sky, and nothing for a
 * cell with nothing to sit on (decision bloom-places-buds-by-biome-and-surface).
 */
class BloomSurfaceTest {

    /**
     * A cell's surroundings set by hand.
     *
     * @param water     still water beneath
     * @param solids    the sides offering a sturdy face
     * @param plantable the ground beneath takes plants
     * @param sky       the sky reaches the cell
     */
    private record Cell(boolean water, Set<Direction> solids, boolean plantable, boolean sky)
            implements BloomSurface.Probe {

        @Override
        public boolean waterBelow() {
            return water;
        }

        @Override
        public boolean solid(Direction side) {
            return solids.contains(side);
        }

        @Override
        public boolean plantableBelow() {
            return plantable;
        }

        @Override
        public boolean skyVisible() {
            return sky;
        }
    }

    private static Optional<BloomSurface.Spot> spotOf(boolean water, Set<Direction> solids, boolean plantable,
                                                    boolean sky) {
        return BloomSurface.classify(new Cell(water, solids, plantable, sky));
    }

    private static Set<Direction> sides(Direction... sides) {
        return sides.length == 0 ? EnumSet.noneOf(Direction.class) : EnumSet.of(sides[0], sides);
    }

    @Test
    void stillWaterBeneathTakesALily() {
        assertEquals(Optional.of(new BloomSurface.Spot(BloomFlora.WATER, Direction.UP)),
                spotOf(true, sides(), false, true));
    }

    @Nested
    class UnderTheSky {

        @Test
        void plantableGroundTakesAFieldPlant() {
            assertEquals(Optional.of(new BloomSurface.Spot(BloomFlora.FIELD, Direction.UP)),
                    spotOf(false, sides(Direction.DOWN), true, true));
        }

        @Test
        void aWallTakesAVineFacingAwayFromIt() {
            assertEquals(Optional.of(new BloomSurface.Spot(BloomFlora.WALL, Direction.WEST)),
                    spotOf(false, sides(Direction.EAST), false, true));
        }

        @Test
        void bareStoneGroundBesideAWallTakesAVine() {
            assertEquals(Optional.of(new BloomSurface.Spot(BloomFlora.WALL, Direction.SOUTH)),
                    spotOf(false, sides(Direction.DOWN, Direction.NORTH), false, true));
        }

        @Test
        void bareStoneGroundAloneHoldsNothing() {
            assertTrue(spotOf(false, sides(Direction.DOWN), false, true).isEmpty());
        }
    }

    @Nested
    class InACave {

        @Test
        void theFloorTakesCaveFlora() {
            assertEquals(Optional.of(new BloomSurface.Spot(BloomFlora.CAVE, Direction.UP)),
                    spotOf(false, sides(Direction.DOWN, Direction.UP), true, false));
        }

        @Test
        void theCeilingTakesCaveFloraHangingDown() {
            assertEquals(Optional.of(new BloomSurface.Spot(BloomFlora.CAVE, Direction.DOWN)),
                    spotOf(false, sides(Direction.UP), false, false));
        }

        @Test
        void aCaveWallTakesCaveFloraNotAVine() {
            assertEquals(Optional.of(new BloomSurface.Spot(BloomFlora.CAVE, Direction.EAST)),
                    spotOf(false, sides(Direction.WEST), false, false));
        }
    }

    @Test
    void aCellWithNothingToSitOnHoldsNothing() {
        assertTrue(spotOf(false, sides(), false, true).isEmpty());
        assertTrue(spotOf(false, sides(), false, false).isEmpty());
    }
}
