package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.block.ability.SlimeFlow.Footing;
import com.mercuriusxeno.goo.block.ability.SlimeFlow.Side;
import com.mercuriusxeno.goo.type.Adhesion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The pour of goo cells joined face to face, worked out at once and pure of
 * any level. The cells a walk reaches form one network, and a layer passes
 * from one cell to a side neighbour only where the pour rules already decided
 * let it: SlimeFlow.pour's cohesion step, the hold a cell's walls keep, and
 * the rise a landing cell may take. The network answers the count each cell
 * rests at, then steps every cell toward it in one direction only, a cell
 * that gains never losing and one that loses never gaining, the layers moved
 * and never made or lost. Ledges keep their own path: the network reads an
 * open place with no top under it as blocked.
 * connected-goo-cells-form-one-network-that-works-out-the-pour
 * spread-decreases-the-cell-it-leaves
 * cohesion-is-the-step-the-goo-holds
 */
final class PourNetwork {

    /** The most cells one network's walk takes in, so a sea of goo costs a bounded walk. */
    static final int MOST_CELLS = 1024;
    /** The layer a giver's count moves by in a step. */
    private static final int GIVES = -1;
    /** The layer a taker's count moves by in a step. */
    private static final int TAKES = 1;
    private static final List<Direction> SIDES =List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH,
            Direction.WEST);

    /** What the places around a network hold, as the pour rules read them. */
    interface Ground {

        /**
         * The layers a cell of the pouring goo holds at a place.
         *
         * @param pos the place
         * @return its layers, empty where no cell of the goo stands
         */
        OptionalInt cellAt(BlockPos pos);

        /**
         * What a place holding no cell of the goo does with a poured layer:
         * HOLDS where open over a top that holds the goo, else BLOCKED, a
         * ledge read as blocked.
         *
         * @param pos the place
         * @return its footing
         */
        Footing openFooting(BlockPos pos);

        /**
         * The layers a cell's walls hold back from any pour, 0 for a cell on a floor.
         * wall-held-goo-keeps-its-hold-over-goo-below
         *
         * @param pos the cell
         * @return the layers held
         */
        int heldAt(BlockPos pos);

        /**
         * Whether a place may rise from one count to another: within a whole
         * block, and around no living thing's feet.
         *
         * @param pos  the place
         * @param from the layers it holds
         * @param to   the layers it would hold
         * @return true where it takes the rise
         */
        boolean rises(BlockPos pos, int from, int to);
    }

    /**
     * One layer passing from a cell to another in a step.
     *
     * @param from the cell it leaves
     * @param to   the cell it lands in
     */
    record Move(BlockPos from, BlockPos to) {
    }

    /**
     * One step of a network's pour.
     *
     * @param layers the count each cell the step changes ends at
     * @param moves  each layer the step passes, from its giver to its taker
     */
    record Step(Map<BlockPos, Integer> layers, List<Move> moves) {
    }

    private PourNetwork() {
    }

    /**
     * The count each cell of a network rests at once no cell pours under the
     * pour rules, worked out by pouring a layer at a time in memory; an open
     * place a layer reaches joins the network.
     *
     * @param from     a cell of the network
     * @param ground   the places around it
     * @param cohesion the goo's cohesion step
     * @param adhesion the goo's adhesion rung
     * @return every cell's resting layers, each cell the walk reached and each open place that took a layer
     */
    static Map<BlockPos, Integer> rest(BlockPos from, Ground ground, int cohesion, Adhesion adhesion) {
        Map<BlockPos, Integer> layers = walk(from, ground);
        boolean poured = true;
        while (poured) {
            poured = false;
            for (BlockPos cell : List.copyOf(layers.keySet())) {
                poured |= pourOne(cell, layers, ground, cohesion, adhesion);
            }
        }
        return layers;
    }

    /**
     * Every cell of the goo joined to one face to face across the level, in the order a breadth-first walk meets
     * them, so the network reads alike from whichever cell it is worked out.
     *
     * @param from   the cell the walk starts at
     * @param ground the places around it
     * @return each joined cell's layers, in walk order
     */
    private static Map<BlockPos, Integer> walk(BlockPos from, Ground ground) {
        Map<BlockPos, Integer> layers = new LinkedHashMap<>();
        Deque<BlockPos> next = new ArrayDeque<>(List.of(from));
        while (!next.isEmpty() && layers.size() < MOST_CELLS) {
            BlockPos cell = next.poll();
            OptionalInt held = ground.cellAt(cell);
            if (layers.containsKey(cell) || held.isEmpty()) {
                continue;
            }
            layers.put(cell, held.getAsInt());
            SIDES.forEach(side -> next.add(cell.relative(side)));
        }
        return layers;
    }

    /**
     * Pours one layer from a cell into the side the pour rule picks among
     * those that take it, as a cell's pour tick does; a cell at or under its
     * walls' hold pours none.
     *
     * @param cell     the pouring cell
     * @param layers   every network cell's layers, changed in place
     * @param ground   the places around the network
     * @param cohesion the goo's cohesion step
     * @param adhesion the goo's adhesion rung
     * @return true where a layer moved
     */
    private static boolean pourOne(BlockPos cell, Map<BlockPos, Integer> layers, Ground ground, int cohesion,
                                   Adhesion adhesion) {
        int held = layers.get(cell);
        if (held <= ground.heldAt(cell)) {
            return false;
        }
        List<Side> sides = new ArrayList<>(SIDES.stream().map(side -> sideOf(cell.relative(side), side, layers,
                ground)).toList());
        for (int tried = 0; tried < SIDES.size(); tried++) {
            Optional<Direction> toward = SlimeFlow.pour(held, cohesion, adhesion, sides);
            if (toward.isEmpty()) {
                return false;
            }
            BlockPos landing = cell.relative(toward.get());
            int taker = layers.getOrDefault(landing, 0);
            if (ground.rises(landing, taker, taker + 1)) {
                layers.put(cell, held - 1);
                layers.put(landing, taker + 1);
                return true;
            }
            sides.replaceAll(side -> side.direction() == toward.get() ? Side.blocked(side.direction()) : side);
        }
        return false;
    }

    private static Side sideOf(BlockPos beside, Direction side, Map<BlockPos, Integer> layers, Ground ground) {
        if (layers.containsKey(beside)) {
            return Side.holds(side, layers.get(beside));
        }
        // a cell the walk's bound left out stands outside this network's pour
        if (ground.cellAt(beside).isPresent()) {
            return Side.blocked(side);
        }
        return ground.openFooting(beside) == Footing.HOLDS ? Side.holds(side, 0) : Side.blocked(side);
    }

    /**
     * One step toward rest: every cell on the side with more cells, givers
     * or takers, moves one layer, and the cells on the other side share those
     * layers out as evenly as each one's way to rest allows, so each cell
     * moves toward its resting count and never past it, and the step moves as
     * many layers out as in.
     *
     * @param now  every cell's layers now
     * @param rest every cell's resting layers
     * @return the step, empty where every cell rests
     */
    static Step stepToward(Map<BlockPos, Integer> now, Map<BlockPos, Integer> rest) {
        List<BlockPos> givers = cellsHeading(now, rest, GIVES);
        List<BlockPos> takers = cellsHeading(now, rest, TAKES);
        return givers.size() >= takers.size() ? paired(givers, takers, now, rest, GIVES)
                : paired(takers, givers, now, rest, TAKES);
    }

    /**
     * The cells whose way to rest runs one way, in the network's order.
     *
     * @param now  every cell's layers now
     * @param rest every cell's resting layers
     * @param way  GIVES for the cells over their rest, TAKES for those under it
     * @return the cells heading that way
     */
    private static List<BlockPos> cellsHeading(Map<BlockPos, Integer> now, Map<BlockPos, Integer> rest, int way) {
        return rest.keySet().stream()
                .filter(cell -> Integer.signum(rest.get(cell) - now.getOrDefault(cell, 0)) == way).toList();
    }

    /**
     * Pairs each layer the wide side moves, one a cell, with a cell of the narrow side sharing them out.
     *
     * @param wide   the side with more cells, each moving one layer
     * @param narrow the side with fewer cells, sharing those layers out
     * @param now    every cell's layers now
     * @param rest   every cell's resting layers
     * @param sign   the way the wide side's cells move, GIVES or TAKES
     * @return the step
     */
    private static Step paired(List<BlockPos> wide, List<BlockPos> narrow, Map<BlockPos, Integer> now,
                               Map<BlockPos, Integer> rest, int sign) {
        List<BlockPos> narrowUnits = unitsOf(narrow, wide.size(), now, rest);
        Map<BlockPos, Integer> layers = new LinkedHashMap<>();
        List<Move> moves = new ArrayList<>();
        for (int unit = 0; unit < narrowUnits.size(); unit++) {
            BlockPos wideCell = wide.get(unit);
            BlockPos narrowCell = narrowUnits.get(unit);
            layers.merge(wideCell, now.getOrDefault(wideCell, 0) + sign, (was, ignored) -> was + sign);
            layers.merge(narrowCell, now.getOrDefault(narrowCell, 0) - sign, (was, ignored) -> was - sign);
            moves.add(sign == GIVES ? new Move(wideCell, narrowCell) : new Move(narrowCell, wideCell));
        }
        return new Step(layers, moves);
    }

    /**
     * The narrow side's cells, one entry for each layer it moves, the layers shared out as evenly as each cell's
     * way to rest allows and no more than the wide side's cells.
     *
     * @param narrow    the side with fewer cells
     * @param wideCells how many cells the wide side holds
     * @param now       every cell's layers now
     * @param rest      every cell's resting layers
     * @return a cell for each layer moved
     */
    private static List<BlockPos> unitsOf(List<BlockPos> narrow, int wideCells, Map<BlockPos, Integer> now,
                                          Map<BlockPos, Integer> rest) {
        int[] owed = narrow.stream().mapToInt(cell -> Math.abs(rest.get(cell) - now.getOrDefault(cell, 0))).toArray();
        int[] shares = shareOut(Math.min(wideCells, Arrays.stream(owed).sum()), owed);
        List<BlockPos> units = new ArrayList<>();
        for (int at = 0; at < narrow.size(); at++) {
            units.addAll(Collections.nCopies(shares[at], narrow.get(at)));
        }
        return units;
    }

    /**
     * Shares layers out over cells a layer a pass, each cell taking no more than it is owed.
     *
     * @param layers the layers to share, no more than the cells are owed in all
     * @param owed   the layers each cell is owed
     * @return each cell's share
     */
    private static int[] shareOut(int layers, int[] owed) {
        int[] shares = new int[owed.length];
        int left = layers;
        while (left > 0) {
            for (int at = 0; at < owed.length && left > 0; at++) {
                if (shares[at] < owed[at]) {
                    shares[at]++;
                    left--;
                }
            }
        }
        return shares;
    }
}
