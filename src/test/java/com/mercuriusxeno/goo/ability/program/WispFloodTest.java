package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WispFlood: the flood lights the cells nearest the eyes first, within a
 * tick's budget, keeps every placed wisp out of another's light and clear
 * of the eyes, passes only through open cells, places only where the eyes
 * see, stops at its range, and picks its dark cells by a roll under the
 * density, so two rolls light two scatters.
 * decision radiant-wisps-where-light-is-low
 * decision radiant-scatters-wisps-at-random-among-the-dark
 */
class WispFloodTest {

    private static final BlockPos EYES = BlockPos.ZERO;
    /** A cave room of open air, 21 cells on a side, around the eyes. */
    private static final int ROOM_HALF = 10;
    private static final double RANGE = 64;
    private static final int TICK_BUDGET = 4000;
    /** Every test that leaves the density at one rolls this, which then decides nothing. */
    private static final RandomSource ROLL = RandomSource.create(0L);
    private static final double HALF_THE_DARK = 0.5;

    @Test
    void theFirstTickLightsTheCellsNearestTheEyes() {
        Grid grid = new Grid(WispFloodTest::inTheRoom, cell -> true);
        WispFlood flood = new WispFlood(EYES, RANGE, 0);
        assertTrue(flood.walk(TICK_BUDGET, grid, ROLL) > 0, "The first tick should place wisps");
        int nearest = grid.placed.stream().mapToInt(cell -> cell.distManhattan(EYES)).min().orElseThrow();
        assertEquals(WispFlood.CLEAR_OF_THE_EYES + 1, nearest);
    }

    @Test
    void noWispSitsInAnotherWispsLightNorAtTheEyes() {
        Grid grid = new Grid(WispFloodTest::inTheRoom, cell -> true);
        new WispFlood(EYES, RANGE, 0).walk(TICK_BUDGET * 3, grid, ROLL);
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
        new WispFlood(EYES, RANGE, 0).walk(TICK_BUDGET * 3, grid, ROLL);
        assertFalse(grid.placed.isEmpty(), "The open side should take wisps");
        assertTrue(grid.placed.stream().allMatch(cell -> cell.getX() < 3), "No wisp should pass the wall");
    }

    @Test
    void theFloodPlacesOnlyWhereTheEyesSee() {
        Grid grid = new Grid(WispFloodTest::inTheRoom, cell -> cell.getY() >= 0);
        new WispFlood(EYES, RANGE, 0).walk(TICK_BUDGET * 3, grid, ROLL);
        assertFalse(grid.placed.isEmpty(), "The seen half should take wisps");
        assertTrue(grid.placed.stream().allMatch(cell -> cell.getY() >= 0), "No wisp should sit out of sight");
    }

    @Test
    void theFloodStopsAtItsRange() {
        double shortRange = 6;
        Grid grid = new Grid(WispFloodTest::inTheRoom, cell -> true);
        new WispFlood(EYES, shortRange, 0).walk(TICK_BUDGET * 3, grid, ROLL);
        assertTrue(grid.placed.stream().allMatch(cell -> cell.distSqr(EYES) <= shortRange * shortRange),
                "No wisp should sit past the range");
    }

    @Test
    void theEdgeGrowsEachTickAndHoldsTheFloodWithinIt() {
        double growth = 0.8;
        int ticks = 10;
        Grid grid = new Grid(WispFloodTest::inTheRoom, cell -> true);
        WispFlood flood = new WispFlood(EYES, RANGE, growth);
        for (int tick = 1; tick <= ticks; tick++) {
            flood.walk(TICK_BUDGET, grid, ROLL);
            double edge = growth * tick;
            assertEquals(edge, flood.edge(), 1e-9);
            assertTrue(grid.placed.stream().allMatch(cell -> cell.distSqr(EYES) <= edge * edge),
                    "No wisp should sit past the edge on tick " + tick);
        }
        assertFalse(grid.placed.isEmpty(), "Eight blocks of edge should reach past the eyes' clear steps");
    }

    @Test
    void theEdgeStopsAtTheRange() {
        double shortRange = 3;
        WispFlood flood = new WispFlood(EYES, shortRange, 2);
        Grid grid = new Grid(WispFloodTest::inTheRoom, cell -> true);
        flood.walk(TICK_BUDGET, grid, ROLL);
        flood.walk(TICK_BUDGET, grid, ROLL);
        assertEquals(shortRange, flood.edge(), 1e-9);
    }

    @Test
    void aShorterLightReachPacksTheWispsCloser() {
        int litSteps = 3;
        Grid grid = new Grid(WispFloodTest::inTheRoom, cell -> true);
        new WispFlood(EYES, RANGE, 0, litSteps, WispFlood.EVERY_DARK_CELL).walk(TICK_BUDGET * 3, grid, ROLL);
        int closest = Integer.MAX_VALUE;
        for (BlockPos wisp : grid.placed) {
            for (BlockPos other : grid.placed) {
                if (!wisp.equals(other)) {
                    closest = Math.min(closest, wisp.distManhattan(other));
                }
            }
        }
        assertEquals(litSteps + 1, closest);
    }

    @Test
    void aMovedFloodForgetsTheWispsPastItsReach() {
        double shortRange = 8;
        WispFlood flood = new WispFlood(EYES, shortRange, 0);
        flood.walk(TICK_BUDGET, new Grid(WispFloodTest::inTheRoom, cell -> true), ROLL);
        assertFalse(flood.placed().isEmpty());
        assertEquals(flood.placed(), flood.movedTo(EYES.east(2)).placed());
        assertTrue(flood.movedTo(EYES.east(100)).placed().isEmpty());
    }

    @Test
    void twoRollsPickTwoScattersAmongTheDarkCells() {
        Grid grid = new Grid(WispFloodTest::inTheRoom, cell -> true);
        Set<BlockPos> dark = new HashSet<>();
        BlockPos.betweenClosed(-ROOM_HALF, -ROOM_HALF, -ROOM_HALF, ROOM_HALF, ROOM_HALF, ROOM_HALF)
                .forEach(cell -> dark.add(cell.immutable()));
        Set<BlockPos> first = picked(dark, grid, RandomSource.create(1L));
        Set<BlockPos> second = picked(dark, grid, RandomSource.create(2L));
        assertNotEquals(first, second, "Two rolls should pick two different scatters");
        for (Set<BlockPos> scatter : List.of(first, second)) {
            assertFalse(scatter.isEmpty(), "A roll at half should pick some dark cells");
            assertTrue(dark.containsAll(scatter) && scatter.size() < dark.size(),
                    "A roll should pick some of the dark cells and leave the rest");
        }
    }

    @Test
    void twoRollsFloodTwoScattersThroughTheRoom() {
        Grid firstGrid = new Grid(WispFloodTest::inTheRoom, cell -> true);
        Grid secondGrid = new Grid(WispFloodTest::inTheRoom, cell -> true);
        new WispFlood(EYES, RANGE, 0, WispFlood.LIT_STEPS, HALF_THE_DARK)
                .walk(TICK_BUDGET * 3, firstGrid, RandomSource.create(1L));
        new WispFlood(EYES, RANGE, 0, WispFlood.LIT_STEPS, HALF_THE_DARK)
                .walk(TICK_BUDGET * 3, secondGrid, RandomSource.create(2L));
        assertFalse(firstGrid.placed.isEmpty(), "The flood should still light the room");
        assertNotEquals(Set.copyOf(firstGrid.placed), Set.copyOf(secondGrid.placed),
                "Two rolls should flood two different scatters");
    }

    private static Set<BlockPos> picked(Set<BlockPos> dark, WispFlood.Cells cells, RandomSource random) {
        Set<BlockPos> picked = new HashSet<>();
        for (BlockPos cell : dark) {
            if (WispFlood.picks(cell, cells, random, HALF_THE_DARK)) {
                picked.add(cell);
            }
        }
        return picked;
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
