package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.throwing.CoatRay;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A drip's strike feeds the cell over a struck top that can hold a world
 * goo, or the cell of its goo it struck short of a whole block; a strike on
 * a side, or on a top that holds none, feeds nothing, for Goop and Slick
 * alike (decisions goop-and-slick-coat-lands-where-each-drip-arc-strikes,
 * slime-and-slick-are-one-volume-cell-tech).
 */
class CellLandingTest {

    private static final BlockPos FLOOR = new BlockPos(8, 0, 2);
    private static final BlockPos CELL = new BlockPos(5, 1, 2);
    private static final BlockPos SLAB = new BlockPos(6, 0, 2);
    private static final BlockPos WALL = new BlockPos(3, 1, 2);

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
    };

    private static CoatRay.BlockStrike strike(BlockPos block, Direction face) {
        return new CoatRay.BlockStrike(Vec3.atCenterOf(block).relative(face, 0.5), block, face);
    }

    @Test
    void aStrikeOnAFloorsTopFeedsTheCellOverIt() {
        assertEquals(Optional.of(FLOOR.above()), CellLanding.cellFedBy(strike(FLOOR, Direction.UP), cells));
    }

    @Test
    void aStrikeOnAStandingCellFeedsThatCell() {
        assertEquals(Optional.of(CELL), CellLanding.cellFedBy(strike(CELL, Direction.UP), cells));
    }

    @Test
    void aStrikeOnATopHoldingNoCellFeedsNothing() {
        assertEquals(Optional.empty(), CellLanding.cellFedBy(strike(SLAB, Direction.UP), cells));
    }

    @Test
    void aStrikeOnAWallsSideFeedsNothing() {
        assertEquals(Optional.empty(), CellLanding.cellFedBy(strike(WALL, Direction.WEST), cells));
    }
}
