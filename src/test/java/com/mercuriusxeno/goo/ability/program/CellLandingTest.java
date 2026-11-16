package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.throwing.CoatRay;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
        public boolean coatsSide(BlockPos cell, Direction side) {
            return !cell.equals(FILMED_WALL.west());
        }
    };

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

    @Test
    void aSideStrikeOnAWallAlreadyFilmedFeedsNothingYet() {
        assertEquals(Optional.empty(),
                CellLanding.cellFedBy(strike(FILMED_WALL, Direction.WEST), HOLDS_WHERE_IT_STRIKES, cells));
    }
}
