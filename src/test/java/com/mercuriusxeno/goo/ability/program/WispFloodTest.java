package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WispFlood: the flood lights the cells nearest the eyes first, within a
 * tick's budget, keeps every placed wisp out of another's light and clear
 * of the eyes, passes only through open cells, places only where the eyes
 * see, and stops at its range.
 * decision radiant-wisps-where-light-is-low
 */
class WispFloodTest {

    private static final BlockPos EYES = BlockPos.ZERO;
    /** A cave room of open air, 21 cells on a side, around the eyes. */
    private static final int ROOM_HALF = 10;
    private static final double RANGE = 64;
    private static final int TICK_BUDGET = 4000;

    @Test
    void theFirstTickLightsTheCellsNearestTheEyes() {
        Grid grid = new Grid(WispFloodTest::inTheRoom, cell -> true);
        WispFlood flood = new WispFlood(EYES, RANGE);
        assertTrue(flood.walk(TICK_BUDGET, grid) > 0, "The first tick should place wisps");
        int nearest = grid.placed.stream().mapToInt(cell -> cell.distManhattan(EYES)).min().orElseThrow();
        assertEquals(WispFlood.CLEAR_OF_THE_EYES + 1, nearest);
    }

    @Test
    void noWispSitsInAnotherWispsLightNorAtTheEyes() {
        Grid grid = new Grid(WispFloodTest::inTheRoom, cell -> true);
        new WispFlood(EYES, RANGE).walk(TICK_BUDGET * 3, grid);
        for (BlockPos wisp : grid.placed) {
            assertTrue(wisp.distManhattan(EYES) > WispFlood.CLEAR_OF_THE_EYES, wisp + " sits at the eyes");
            for (BlockPos other : grid.placed) {
                assertTrue(wisp.equals(other) || wisp.distManhattan(other) > WispFlood.LIT_STEPS,
                        wisp + " sits in the light of " + other);
            }
        }
    }

    @Test
    void theFloodPassesOnlyThroughOpenCells() {
        Grid grid = new Grid(cell -> inTheRoom(cell) && cell.getX() < 3, cell -> true);
        new WispFlood(EYES, RANGE).walk(TICK_BUDGET * 3, grid);
        assertFalse(grid.placed.isEmpty(), "The open side should take wisps");
        assertTrue(grid.placed.stream().allMatch(cell -> cell.getX() < 3), "No wisp should pass the wall");
    }

    @Test
    void theFloodPlacesOnlyWhereTheEyesSee() {
        Grid grid = new Grid(WispFloodTest::inTheRoom, cell -> cell.getY() >= 0);
        new WispFlood(EYES, RANGE).walk(TICK_BUDGET * 3, grid);
        assertFalse(grid.placed.isEmpty(), "The seen half should take wisps");
        assertTrue(grid.placed.stream().allMatch(cell -> cell.getY() >= 0), "No wisp should sit out of sight");
    }

    @Test
    void theFloodStopsAtItsRange() {
        double shortRange = 6;
        Grid grid = new Grid(WispFloodTest::inTheRoom, cell -> true);
        new WispFlood(EYES, shortRange).walk(TICK_BUDGET * 3, grid);
        assertTrue(grid.placed.stream().allMatch(cell -> cell.distSqr(EYES) <= shortRange * shortRange),
                "No wisp should sit past the range");
    }

    private static boolean inTheRoom(BlockPos cell) {
        return Math.abs(cell.getX()) <= ROOM_HALF && Math.abs(cell.getY()) <= ROOM_HALF
                && Math.abs(cell.getZ()) <= ROOM_HALF;
    }

    /** A dark grid: open where the room says, seen where the sight says, recording each wisp placed. */
    private static final class Grid implements WispFlood.Cells {
        private final Predicate<BlockPos> room;
        private final Predicate<BlockPos> sight;
        private final List<BlockPos> placed = new ArrayList<>();

        Grid(Predicate<BlockPos> room, Predicate<BlockPos> sight) {
            this.room = room;
            this.sight = sight;
        }

        @Override
        public boolean open(BlockPos cell) {
            return room.test(cell);
        }

        @Override
        public boolean dark(BlockPos cell) {
            return room.test(cell) && !placed.contains(cell);
        }

        @Override
        public boolean seen(BlockPos cell) {
            return sight.test(cell);
        }

        @Override
        public void place(BlockPos cell) {
            placed.add(cell.immutable());
        }
    }
}
