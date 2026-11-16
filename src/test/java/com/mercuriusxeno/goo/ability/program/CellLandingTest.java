package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.block.ability.SideCoat;
import com.mercuriusxeno.goo.throwing.CoatRay;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A drip's strike feeds the cell over a struck top that can hold a world
 * goo, or the cell of its goo it struck short of a whole block; a strike on
 * a block's side at adhesion 3 coats that side's wall in the open cell
 * beside it; a strike on a top that holds none feeds nothing, for Goop and
 * Slick alike (decisions goop-and-slick-coat-lands-where-each-drip-arc-strikes,
 * slime-and-slick-are-one-volume-cell-tech, drip-striking-a-side-sticks-or-runs-by-adhesion,
 * adhesion-is-a-ladder-of-holds).
 */
class CellLandingTest {

    private static final BlockPos FLOOR = new BlockPos(8, 0, 2);
    private static final BlockPos CELL = new BlockPos(5, 1, 2);
    private static final BlockPos SLAB = new BlockPos(6, 0, 2);
    private static final BlockPos WALL = new BlockPos(3, 1, 2);
    /** A wall whose west neighbour already holds a film against it. */
    private static final BlockPos FILMED_WALL = new BlockPos(3, 1, 9);
    private static final int HOLDS_WHERE_IT_STRIKES = 3;
    /** The cell under an overhang, walled east and south. */
    private static final BlockPos UNDER_THE_OVERHANG = new BlockPos(12, 2, 12);
    /** An overhang whose cell below touches no wall. */
    private static final BlockPos OPEN_OVERHANG = new BlockPos(20, 5, 20);
    /** The floor an open overhang's drip drops to. */
    private static final BlockPos DROP_FLOOR = new BlockPos(20, 0, 20);
    private static final int HOLDS_TOPS = 1;

    /** Open cells stand over every top but the cell's and the slab's; the standing cell alone thickens. */
    private final CellLanding.Cells cells = new CellLanding.Cells() {
        @Override
        public boolean thickens(BlockPos pos) {
            return pos.equals(CELL);
        }

        @Override
        public boolean takesACell(BlockPos pos) {
            return !pos.equals(CELL) && !pos.equals(CELL.above()) && !pos.equals(SLAB.above());
        }

        @Override
        public Optional<SideCoat> sideAt(BlockPos cell, Direction side) {
            if (cell.equals(WALL) || cell.equals(FILMED_WALL) || cell.equals(CELL)) {
                return Optional.empty();
            }
            boolean filmed = cell.equals(FILMED_WALL.west()) && side == Direction.EAST;
            return Optional.of(filmed ? SideCoat.FILM : SideCoat.BARE);
        }

        @Override
        public boolean gooBelow(BlockPos cell) {
            return false;
        }

        @Override
        public boolean wallAt(BlockPos cell, Direction side) {
            return cell.equals(UNDER_THE_OVERHANG) && (side == Direction.EAST || side == Direction.SOUTH);
        }

        @Override
        public Optional<BlockPos> dropLanding(BlockPos cell) {
            return Optional.of(DROP_FLOOR.above());
        }
    };

    /**
     * Goo holds no ceiling: a drip striking a block's underside runs down a
     * wall the cell under it touches, the nearest, at any adhesion, and
     * where it touches none drops to the first top below
     * (decision drip-striking-a-ceiling-runs-down-a-wall-or-drops).
     */
    @Nested
    class Underside {

        @Test
        void aStrikeOnAnUndersideBesideAWallRunsDownTheNearestWall() {
            BlockPos overhang = UNDER_THE_OVERHANG.above();
            CoatRay.BlockStrike nearTheEast = new CoatRay.BlockStrike(
                    new Vec3(overhang.getX() + 0.9, overhang.getY(), overhang.getZ() + 0.5), overhang, Direction.DOWN);

            assertEquals(Optional.of(CellFed.runny(UNDER_THE_OVERHANG, Direction.EAST)),
                    CellLanding.cellFedBy(nearTheEast, HOLDS_WHERE_IT_STRIKES, cells));
        }

        @Test
        void aStrikeOnAnUndersideTouchingNoWallDropsToTheFirstTopBelow() {
            assertEquals(Optional.of(CellFed.fill(DROP_FLOOR.above())),
                    CellLanding.cellFedBy(strike(OPEN_OVERHANG, Direction.DOWN), 0, cells));
        }
    }

    /**
     * What a side strike lays follows the adhesion ladder, read whole without a
     * goo dialed to each rung (decisions adhesion-is-a-ladder-of-holds,
     * drip-striking-a-side-sticks-or-runs-by-adhesion).
     */
    @Nested
    class Ladder {

        @Test
        void adhesionZeroAndOneRunAtABareSide() {
            assertEquals(SideCoat.RUNNY, CellLanding.sideCoatFor(0, SideCoat.BARE, true));
            assertEquals(SideCoat.RUNNY, CellLanding.sideCoatFor(HOLDS_TOPS, SideCoat.BARE, true));
        }

        @Test
        void adhesionTwoHoldsOnlyOverGooOfItsType() {
            assertEquals(SideCoat.FILM, CellLanding.sideCoatFor(2, SideCoat.BARE, true));
            assertEquals(SideCoat.RUNNY, CellLanding.sideCoatFor(2, SideCoat.BARE, false));
        }

        @Test
        void adhesionThreeHoldsWhereItStrikes() {
            assertEquals(SideCoat.FILM, CellLanding.sideCoatFor(HOLDS_WHERE_IT_STRIKES, SideCoat.BARE, false));
        }

        @ParameterizedTest
        @ValueSource(ints = {0, 1, 2, 3})
        void aSideAlreadyHoldingItsLayerRunsAtAnyAdhesion(int adhesion) {
            assertEquals(SideCoat.RUNNY, CellLanding.sideCoatFor(adhesion, SideCoat.FILM, true));
        }
    }

    @Test
    void aSideStrikeAtAdhesionZeroRunsDownTheWallInTheCellBesideIt() {
        assertEquals(Optional.of(CellFed.runny(WALL.west(), Direction.EAST)),
                CellLanding.cellFedBy(strike(WALL, Direction.WEST), 0, cells));
    }

    private static CoatRay.BlockStrike strike(BlockPos block, Direction face) {
        return new CoatRay.BlockStrike(Vec3.atCenterOf(block).relative(face, 0.5), block, face);
    }

    @Test
    void aStrikeOnAFloorsTopFeedsTheCellOverIt() {
        assertEquals(Optional.of(CellFed.fill(FLOOR.above())),
                CellLanding.cellFedBy(strike(FLOOR, Direction.UP), HOLDS_TOPS, cells));
    }

    @Test
    void aStrikeOnAStandingCellFeedsThatCell() {
        assertEquals(Optional.of(CellFed.fill(CELL)), CellLanding.cellFedBy(strike(CELL, Direction.UP), HOLDS_TOPS,
                cells));
    }

    @Test
    void aStrikeOnATopHoldingNoCellFeedsNothing() {
        assertEquals(Optional.empty(), CellLanding.cellFedBy(strike(SLAB, Direction.UP), HOLDS_TOPS, cells));
    }

    /**
     * At adhesion 3 a side strike coats the open cell beside the struck face,
     * on the cell's side facing the struck block.
     */
    @ParameterizedTest
    @EnumSource(value = Direction.class, names = {"NORTH", "EAST", "SOUTH", "WEST"})
    void aSideStrikeAtAdhesionThreeCoatsTheWallInTheCellBesideIt(Direction face) {
        assertEquals(Optional.of(CellFed.film(WALL.relative(face), face.getOpposite())),
                CellLanding.cellFedBy(strike(WALL, face), HOLDS_WHERE_IT_STRIKES, cells));
    }

    /**
     * A drip striking a film's own face, the slab standing out from its wall,
     * strikes the wall the film coats and runs there.
     */
    @Test
    void aStrikeOnAFilmsFaceRunsOnThatFilmsSide() {
        assertEquals(Optional.of(CellFed.runny(FILMED_WALL.west(), Direction.EAST)),
                CellLanding.cellFedBy(strike(FILMED_WALL.west(), Direction.WEST), HOLDS_WHERE_IT_STRIKES, cells));
    }

    /**
     * A drip striking the side of its goo's fill joins that fill, as one striking its top does.
     */
    @Test
    void aStrikeOnTheSideOfItsGoosFillJoinsThatFill() {
        assertEquals(Optional.of(CellFed.fill(CELL)), CellLanding.cellFedBy(strike(CELL, Direction.WEST), 0, cells));
    }

    @Test
    void aSideStrikeOnAWallAlreadyFilmedRuns() {
        assertEquals(Optional.of(CellFed.runny(FILMED_WALL.west(), Direction.EAST)),
                CellLanding.cellFedBy(strike(FILMED_WALL, Direction.WEST), HOLDS_WHERE_IT_STRIKES, cells));
    }
}
