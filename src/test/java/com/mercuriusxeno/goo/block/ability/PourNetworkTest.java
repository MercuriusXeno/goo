package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.block.ability.SlimeFlow.Footing;
import com.mercuriusxeno.goo.block.ability.SlimeFlow.Side;
import com.mercuriusxeno.goo.type.Adhesion;
import com.mercuriusxeno.goo.type.Cohesion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers PourNetwork, the pour of goo cells joined face to face worked out at
 * once: the resting counts it answers under the pour rules, and the steps
 * that carry each cell to them, on a fake ground with no level.
 */
class PourNetworkTest {

    /** The floor's last column and row: a walled floor four cells by four, as PourCostTests lays. */
    private static final int FLOOR_TO = 3;
    private static final int WHOLE_BLOCK = 15;
    /** Steps far past any mound's pour, so a network that never rests fails rather than hangs. */
    private static final int MOST_STEPS = 200;
    private static final List<Direction> SIDES = List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH,
            Direction.WEST);

    @Nested
    class StepsToRest {

        /**
         * A mound of four full runny cells in a corner of a walled floor steps to rest with
         * each cell's count only rising or only falling.
         * connected-goo-cells-form-one-network-that-works-out-the-pour
         */
        @Test
        void everyCellRisesOrFallsOneWayToRest() {
            FakeGround ground = FakeGround.moundOnAWalledFloor();
            Map<BlockPos, Integer> rest = PourNetwork.rest(FakeGround.MOUND.getFirst(), ground,
                    Cohesion.RUNNY.threshold(), Adhesion.SLIPS);
            Map<BlockPos, List<Integer>> counts = new LinkedHashMap<>();
            Map<BlockPos, Integer> now = new HashMap<>();
            rest.keySet().forEach(cell -> now.put(cell, ground.cellAt(cell).orElse(0)));
            now.forEach((cell, layers) -> counts.computeIfAbsent(cell, key -> new ArrayList<>()).add(layers));
            for (int step = 0; step < MOST_STEPS && !now.equals(rest); step++) {
                now.putAll(PourNetwork.stepToward(now, rest).layers());
                now.forEach((cell, layers) -> counts.get(cell).add(layers));
            }
            assertEquals(rest, now, "the steps should carry every cell to its resting count");
            counts.forEach((cell, seen) -> assertTrue(oneWay(seen),
                    cell + " should only rise or only fall on its way to rest, went " + seen));
            assertTrue(rest.size() > FakeGround.MOUND.size(), "the mound should spread past its own cells");
        }

        private static boolean oneWay(List<Integer> seen) {
            boolean rose = false;
            boolean fell = false;
            for (int at = 1; at < seen.size(); at++) {
                rose |= seen.get(at) > seen.get(at - 1);
                fell |= seen.get(at) < seen.get(at - 1);
            }
            return !(rose && fell);
        }
    }

    @Nested
    class RestHoldsThePourRules {

        /**
         * A runny mound rests where no cell pours under SlimeFlow.pour, holding the layers poured.
         * connected-goo-cells-form-one-network-that-works-out-the-pour
         * cohesion-is-the-step-the-goo-holds
         */
        @Test
        void runnyMoundRestsWhereNoCellPoursKeepingItsLayers() {
            assertRestsUnderThePourRule(Cohesion.RUNNY.threshold(), Adhesion.SLIPS);
        }

        /**
         * A thick mound, its cohesion step above zero, rests where no cell pours, holding the layers poured.
         * connected-goo-cells-form-one-network-that-works-out-the-pour
         * cohesion-is-the-step-the-goo-holds
         */
        @Test
        void thickMoundRestsWhereNoCellPoursKeepingItsLayers() {
            assertRestsUnderThePourRule(Cohesion.THICK.threshold(), Adhesion.TOPS);
        }

        /**
         * A cell its walls hold, at its hold, beside a lower cell on a floor it
         * would pour into were it not held, keeps every layer, and so does the lower cell.
         * connected-goo-cells-form-one-network-that-works-out-the-pour
         * wall-held-goo-keeps-its-hold-over-goo-below
         */
        @Test
        void wallHeldCellKeepsItsHoldBesideALowerCell() {
            int cohesion = Cohesion.SOFT.threshold();
            int hold = VolumeCellBlock.holdOf(cohesion, 2);
            BlockPos held = new BlockPos(0, 0, 0);
            BlockPos lower = held.east();
            FakeGround ground = new FakeGround(Map.of(held, hold, lower, 1), Set.of(), Map.of(held, hold));
            Map<BlockPos, Integer> rest = PourNetwork.rest(held, ground, cohesion, Adhesion.CLINGS);
            assertEquals(Map.of(held, hold, lower, 1), rest);
            assertTrue(SlimeFlow.pour(hold, cohesion, Adhesion.CLINGS, ground.sidesOf(held, rest)).isPresent(),
                    "the held cell would pour into the lower one were its walls not holding it");
        }

        private static void assertRestsUnderThePourRule(int cohesion, Adhesion adhesion) {
            FakeGround ground = FakeGround.moundOnAWalledFloor();
            Map<BlockPos, Integer> rest = PourNetwork.rest(FakeGround.MOUND.getFirst(), ground, cohesion, adhesion);
            rest.forEach((cell, layers) -> assertTrue(
                    SlimeFlow.pour(layers, cohesion, adhesion, ground.sidesOf(cell, rest)).isEmpty(),
                    cell + " should pour no layer at rest, holds " + layers + " in " + rest));
            int poured = FakeGround.MOUND.size() * WHOLE_BLOCK;
            assertEquals(poured, rest.values().stream().mapToInt(Integer::intValue).sum(),
                    "the network should rest holding the layers poured");
        }
    }

    /**
     * A ground of goo cells, open places over a top that hold a poured layer, and blocked places.
     *
     * @param cells the goo cells standing, each with its layers
     * @param open  the open places over a top
     * @param held  the layers each cell's walls hold back, 0 for any not listed
     */
    private record FakeGround(Map<BlockPos, Integer> cells, Set<BlockPos> open, Map<BlockPos, Integer> held)
            implements PourNetwork.Ground {

        /** The mound's four full cells, a corner of the walled floor, so the cells past them pass goo on. */
        static final List<BlockPos> MOUND = List.of(new BlockPos(0, 0, 0), new BlockPos(1, 0, 0),
                new BlockPos(0, 0, 1), new BlockPos(1, 0, 1));

        static FakeGround moundOnAWalledFloor() {
            Map<BlockPos, Integer> cells = new HashMap<>();
            MOUND.forEach(cell -> cells.put(cell, WHOLE_BLOCK));
            Set<BlockPos> open = new HashSet<>();
            for (int east = 0; east <= FLOOR_TO; east++) {
                for (int south = 0; south <= FLOOR_TO; south++) {
                    open.add(new BlockPos(east, 0, south));
                }
            }
            open.removeAll(cells.keySet());
            return new FakeGround(cells, open, Map.of());
        }

        @Override
        public OptionalInt cellAt(BlockPos pos) {
            return cells.containsKey(pos) ? OptionalInt.of(cells.get(pos)) : OptionalInt.empty();
        }

        @Override
        public Footing openFooting(BlockPos pos) {
            return open.contains(pos) ? Footing.HOLDS : Footing.BLOCKED;
        }

        @Override
        public int heldAt(BlockPos pos) {
            return held.getOrDefault(pos, 0);
        }

        @Override
        public boolean rises(BlockPos pos, int from, int to) {
            return to <= WHOLE_BLOCK;
        }

        /**
         * A cell's sides as a resting network shows them, read apart from PourNetwork's own reading.
         */
        List<Side> sidesOf(BlockPos cell, Map<BlockPos, Integer> network) {
            return SIDES.stream().map(side -> {
                BlockPos beside = cell.relative(side);
                if (network.containsKey(beside)) {
                    return Side.holds(side, network.get(beside));
                }
                return open.contains(beside) ? Side.holds(side, 0) : Side.blocked(side);
            }).toList();
        }
    }
}
