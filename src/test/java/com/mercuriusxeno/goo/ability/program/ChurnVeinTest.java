package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Churn finds the contiguous vein of the first mineral down the struck
 * column and lifts it a layer a step, the ground above sinking into its
 * place, stopping at the ceiling, at air and at ground that will not give
 * way, and conserving every block (decision churn-pulls-a-vein-up-as-a-fluid-chain).
 */
class ChurnVeinTest {

    private static final BlockPos STRUCK = new BlockPos(10, 64, -5);
    private static final String STONE = "stone";
    private static final String ORE = "ore";
    private static final String OTHER_ORE = "other_ore";
    private static final String AIR = "air";
    private static final int DEPTH = 4;
    private static final int LIMIT = 64;

    /** A box of stone with blocks laid in it, air outside. */
    private static final class FakeCells implements ChurnVein.Cells<String> {
        private final Map<BlockPos, String> blocks = new HashMap<>();
        private final Set<BlockPos> fixed = new HashSet<>();

        FakeCells() {
            for (BlockPos pos : BlockPos.betweenClosed(STRUCK.offset(-3, -8, -3), STRUCK.offset(3, 0, 3))) {
                blocks.put(pos.immutable(), STONE);
            }
        }

        FakeCells lay(BlockPos offset, String block) {
            blocks.put(STRUCK.offset(offset), block);
            return this;
        }

        @Override
        public String get(BlockPos pos) {
            return blocks.getOrDefault(pos, AIR);
        }

        @Override
        public void set(BlockPos pos, String block) {
            blocks.put(pos, block);
        }

        @Override
        public boolean mineral(BlockPos pos) {
            return get(pos).endsWith(ORE);
        }

        @Override
        public boolean movable(BlockPos pos) {
            return !fixed.contains(pos);
        }

        @Override
        public boolean givesWay(BlockPos pos) {
            return movable(pos) && !AIR.equals(get(pos));
        }

        List<String> sortedBlocks() {
            List<String> all = new ArrayList<>(blocks.values());
            all.sort(null);
            return all;
        }
    }

    private static List<BlockPos> pullToRest(List<BlockPos> vein, FakeCells cells) {
        List<BlockPos> standing = vein;
        for (int step = 0; step < LIMIT; step++) {
            List<BlockPos> risen = ChurnVein.rise(standing, STRUCK.getY(), cells);
            if (risen.equals(standing)) {
                return risen;
            }
            standing = risen;
        }
        return standing;
    }

    @Nested
    class Finding {

        @Test
        void takesTheFirstMineralDownTheColumnAndItsJoinedCells() {
            FakeCells cells = new FakeCells().lay(new BlockPos(0, -2, 0), ORE).lay(new BlockPos(1, -2, 0), ORE)
                    .lay(new BlockPos(1, -3, 1), ORE).lay(new BlockPos(0, -3, 0), OTHER_ORE);
            List<BlockPos> vein = ChurnVein.find(STRUCK, DEPTH, LIMIT, cells);
            assertEquals(Set.of(STRUCK.offset(0, -2, 0), STRUCK.offset(1, -2, 0)), Set.copyOf(vein));
        }

        @Test
        void findsNothingPastTheDepth() {
            FakeCells cells = new FakeCells().lay(new BlockPos(0, -DEPTH, 0), ORE);
            assertEquals(List.of(), ChurnVein.find(STRUCK, DEPTH, LIMIT, cells));
        }

        @Test
        void stopsAtTheLimit() {
            FakeCells cells = new FakeCells();
            for (int down = 1; down <= 6; down++) {
                cells.lay(new BlockPos(0, -down, 0), ORE);
            }
            assertEquals(3, ChurnVein.find(STRUCK, DEPTH, 3, cells).size());
        }
    }

    @Nested
    class Rising {

        @Test
        void liftsTheVeinToTheStruckLayerAndSinksTheGroundToItsFoot() {
            FakeCells cells = new FakeCells().lay(new BlockPos(0, -3, 0), ORE).lay(new BlockPos(0, -4, 0), ORE)
                    .lay(new BlockPos(0, -1, 0), "dirt");
            pullToRest(ChurnVein.find(STRUCK, DEPTH, LIMIT, cells), cells);
            assertEquals(List.of(ORE, ORE, STONE, "dirt", STONE), List.of(cells.get(STRUCK),
                    cells.get(STRUCK.below(1)), cells.get(STRUCK.below(2)), cells.get(STRUCK.below(3)),
                    cells.get(STRUCK.below(4))));
        }

        @Test
        void risesOneLayerAStep() {
            FakeCells cells = new FakeCells().lay(new BlockPos(0, -3, 0), ORE);
            List<BlockPos> risen = ChurnVein.rise(List.of(STRUCK.below(3)), STRUCK.getY(), cells);
            assertEquals(List.of(STRUCK.below(2)), risen);
        }

        @Test
        void stopsUnderGroundThatWillNotGiveWay() {
            FakeCells cells = new FakeCells().lay(new BlockPos(0, -3, 0), ORE);
            cells.fixed.add(STRUCK.below(1));
            List<BlockPos> rest = pullToRest(List.of(STRUCK.below(3)), cells);
            assertEquals(List.of(STRUCK.below(2)), rest);
        }

        @Test
        void stopsUnderAir() {
            FakeCells cells = new FakeCells().lay(new BlockPos(0, -3, 0), ORE).lay(new BlockPos(0, -1, 0), AIR);
            List<BlockPos> rest = pullToRest(List.of(STRUCK.below(3)), cells);
            assertEquals(List.of(STRUCK.below(2)), rest);
        }

        @Test
        void conservesEveryBlock() {
            FakeCells cells = new FakeCells().lay(new BlockPos(0, -2, 0), ORE).lay(new BlockPos(1, -2, 0), ORE)
                    .lay(new BlockPos(1, -3, 0), ORE).lay(new BlockPos(0, -1, 0), "dirt");
            List<String> before = cells.sortedBlocks();
            List<BlockPos> rest = pullToRest(ChurnVein.find(STRUCK, DEPTH, LIMIT, cells), cells);
            pullToRest(ChurnVein.find(STRUCK, DEPTH, LIMIT, cells), cells);
            assertEquals(before, cells.sortedBlocks());
            assertEquals(Set.of(STRUCK, STRUCK.offset(1, 0, 0), STRUCK.offset(1, -1, 0)), Set.copyOf(rest));
        }
    }
}
