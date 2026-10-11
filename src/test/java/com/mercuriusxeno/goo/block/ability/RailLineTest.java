package com.mercuriusxeno.goo.block.ability;

import java.util.List;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RailLine: the cells a rail runs through between two reflectors, ends left
 * out, unbroken from one end to the other in any direction.
 * decision reflector-rails-carry-the-brightest-light
 */
class RailLineTest {

    private static final BlockPos FROM = new BlockPos(0, 64, 0);

    @Test
    void anAxisLineHoldsEveryCellBetween() {
        List<BlockPos> line = RailLine.between(FROM, FROM.east(10));
        assertEquals(9, line.size());
        for (int x = 1; x <= 9; x++) {
            assertTrue(line.contains(FROM.east(x)), "missing " + FROM.east(x));
        }
    }

    @Test
    void neighborsHaveNoRail() {
        assertTrue(RailLine.between(FROM, FROM.above()).isEmpty());
    }

    @Test
    void aDiagonalLineRunsUnbrokenAndLeavesItsEndsOut() {
        BlockPos to = FROM.offset(5, 4, 5);
        List<BlockPos> line = RailLine.between(FROM, to);
        assertFalse(line.contains(FROM) || line.contains(to), "an end is in the line");
        BlockPos previous = FROM;
        for (BlockPos cell : line) {
            assertTrue(cell.distManhattan(previous) <= 3 && cell.distSqr(previous) <= 3, cell + " jumps from " + previous);
            previous = cell;
        }
        assertTrue(to.distSqr(previous) <= 3, "the line stops short of its far end");
    }
}
