package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Churn's core-to-strip map is a bijection that turns with the footprint,
 * and a churned column conserves every block, swaps core and strips after
 * one turn per layer, and leaves a pinned cell's block in place
 * (decision churn-rotates-a-plus-shaped-column).
 */
class ChurnColumnTest {

    private static final BlockPos ORIGIN = new BlockPos(100, 40, -20);
    private static final int DEPTH = 5;

    /** A column of strings, each cell named by where it started. */
    private static final class FakeCells implements ChurnColumn.Cells<String> {
        private final Map<BlockPos, String> blocks = new HashMap<>();
        private final Set<BlockPos> pins = new HashSet<>();

        @Override
        public String get(BlockPos pos) {
            return blocks.getOrDefault(pos, "air");
        }

        @Override
        public void set(BlockPos pos, String block) {
            blocks.put(pos, block);
        }

        @Override
        public boolean pinned(BlockPos pos) {
            return pins.contains(pos);
        }
    }

    private static FakeCells namedColumn() {
        FakeCells cells = new FakeCells();
        for (int core = 0; core < ChurnMap.CELLS; core++) {
            for (BlockPos pos : ChurnColumn.loop(ORIGIN, DEPTH, core)) {
                cells.set(pos, pos.toShortString());
            }
        }
        return cells;
    }

    private static List<String> sorted(FakeCells cells) {
        List<String> all = new ArrayList<>(cells.blocks.values());
        all.sort(null);
        return all;
    }

    @Nested
    class TheMap {

        @Test
        void sixteenCoreCellsFeedSixteenDistinctStripCellsOutsideTheCore() {
            Set<List<Integer>> strips = new HashSet<>();
            for (int core = 0; core < ChurnMap.CELLS; core++) {
                int[] strip = ChurnMap.stripCellOf(core);
                strips.add(List.of(strip[0], strip[1]));
                boolean outsideCore = strip[0] < 0 || strip[0] >= ChurnMap.SIDE
                        || strip[1] < 0 || strip[1] >= ChurnMap.SIDE;
                assertTrue(outsideCore, Arrays.toString(strip));
            }
            assertEquals(ChurnMap.CELLS, strips.size());
        }

        @Test
        void eachStripCellTouchesASideOfTheCoreAndNoCorner() {
            for (int core = 0; core < ChurnMap.CELLS; core++) {
                int[] strip = ChurnMap.stripCellOf(core);
                boolean alongNorthOrSouth = strip[0] >= 0 && strip[0] < ChurnMap.SIDE;
                boolean alongEastOrWest = strip[1] >= 0 && strip[1] < ChurnMap.SIDE;
                assertTrue(alongNorthOrSouth ^ alongEastOrWest, Arrays.toString(strip));
            }
        }

        @Test
        void aQuarterTurnOfTheFootprintTurnsTheMap() {
            for (int core = 0; core < ChurnMap.CELLS; core++) {
                int[] cell = ChurnMap.coreCell(core);
                int[] strip = ChurnMap.stripCellOf(core);
                int turned = ChurnMap.core(ChurnMap.SIDE - 1 - cell[1], cell[0]);
                int[] turnedStrip = ChurnMap.stripCellOf(turned);
                assertEquals(List.of(ChurnMap.SIDE - 1 - strip[1], strip[0]),
                        List.of(turnedStrip[0], turnedStrip[1]), "core " + Arrays.toString(cell));
            }
        }

        @Test
        void edgeCellsFeedTheStripBesideThem() {
            assertEquals(List.of(1, -1), List.of(ChurnMap.stripCellOf(ChurnMap.core(1, 0))[0],
                    ChurnMap.stripCellOf(ChurnMap.core(1, 0))[1]));
            assertEquals(List.of(0, -1), List.of(ChurnMap.stripCellOf(ChurnMap.core(0, 0))[0],
                    ChurnMap.stripCellOf(ChurnMap.core(0, 0))[1]));
        }
    }

    @Nested
    class TheColumn {

        @Test
        void aTurnConservesEveryBlock() {
            FakeCells cells = namedColumn();
            List<String> before = sorted(cells);
            for (int turn = 0; turn < 2 * DEPTH + 1; turn++) {
                ChurnColumn.turn(ORIGIN, DEPTH, cells);
                assertEquals(before, sorted(cells));
            }
        }

        @Test
        void aTurnRaisesTheCoreAndSinksTheStrips() {
            FakeCells cells = namedColumn();
            BlockPos coreLow = ORIGIN.offset(2, -DEPTH + 1, 1);
            BlockPos stripTop = ORIGIN.offset(ChurnMap.stripCellOf(ChurnMap.core(2, 1))[0], 0,
                    ChurnMap.stripCellOf(ChurnMap.core(2, 1))[1]);
            String coreTopBlock = cells.get(ORIGIN.offset(2, 0, 1));
            String coreLowBlock = cells.get(coreLow);
            ChurnColumn.turn(ORIGIN, DEPTH, cells);
            assertEquals(coreLowBlock, cells.get(coreLow.above()));
            assertEquals(coreTopBlock, cells.get(stripTop));
        }

        @Test
        void oneTurnPerLayerSwapsCoreAndStripsUpsideDown() {
            FakeCells cells = namedColumn();
            int core = ChurnMap.core(0, 3);
            List<BlockPos> loop = ChurnColumn.loop(ORIGIN, DEPTH, core);
            List<String> before = loop.stream().map(cells::get).toList();
            for (int turn = 0; turn < DEPTH; turn++) {
                ChurnColumn.turn(ORIGIN, DEPTH, cells);
            }
            for (int i = 0; i < 2 * DEPTH; i++) {
                assertEquals(before.get(i), cells.get(loop.get((i + DEPTH) % (2 * DEPTH))));
            }
            // The core's top block now lies at the strip's bottom: the top went down.
            assertEquals(before.get(DEPTH - 1), cells.get(loop.get(2 * DEPTH - 1)));
        }

        @Test
        void aPinnedCellKeepsItsBlockAndTheLoopPassesOverIt() {
            FakeCells cells = namedColumn();
            List<BlockPos> loop = ChurnColumn.loop(ORIGIN, DEPTH, ChurnMap.core(1, 1));
            BlockPos pinned = loop.get(2);
            cells.pins.add(pinned);
            String pinnedBlock = cells.get(pinned);
            String passing = cells.get(loop.get(1));
            List<String> before = sorted(cells);
            ChurnColumn.turn(ORIGIN, DEPTH, cells);
            assertEquals(pinnedBlock, cells.get(pinned));
            assertEquals(passing, cells.get(loop.get(3)));
            assertEquals(before, sorted(cells));
        }
    }
}
