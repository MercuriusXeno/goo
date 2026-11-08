package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

/**
 * Radiant's flood: from the holder's eye cell outward through connected
 * open air, nearest cells first, a wisp in each dark cell the holder sees
 * and that no wisp this flood placed already lights. Its edge grows a set
 * number of blocks each tick it walks, and each tick walks up to a budget
 * of cells within the edge, so the light spreads out from the holder at a
 * steady pace while the channel holds, up to its range or the bounds of
 * its visited cells. A holder walking on carries the flood with them.
 * Each dark, sighted cell the walk reaches takes a wisp only by a chance,
 * the density, so the lit cells scatter among the dark rather than fall
 * on a grid, and the walk goes on through the cells it skipped.
 * decision radiant-wisps-where-light-is-low
 * decision radiant-scatters-wisps-at-random-among-the-dark
 * operator rulings 2026-10-10: wisps flood out from the eyes nearest first to 64 blocks, the edge at 16 blocks a second
 */
public final class WispFlood {

    /**
     * How far a fresh wisp's light reaches before a cell reads dark again, at
     * the default threshold: a wisp gives 15, the threshold sits at 8, and
     * light drops one a step, so a cell seven steps out still reads 8, lit.
     */
    static final int LIT_STEPS = 7;
    /** How close to the eyes a wisp may sit, in steps: none in the holder's face. */
    static final int CLEAR_OF_THE_EYES = 2;
    /** The most cells one flood remembers, so open sky never grows it without end. */
    static final int MOST_VISITED = 300_000;
    /** The density a flood lights every dark, sighted cell at. */
    static final double EVERY_DARK_CELL = 1.0;

    private final BlockPos origin;
    private final double range;
    private final double growth;
    private final int litSteps;
    private final double density;
    private double edge;
    private final Deque<BlockPos> frontier = new ArrayDeque<>();
    private final LongOpenHashSet visited = new LongOpenHashSet();
    private final List<BlockPos> placed = new ArrayList<>();
    private long lastWalked;

    /**
     * Starts a flood at a cell.
     *
     * @param origin the holder's eye cell
     * @param range  how far from the origin a cell may be, in blocks
     * @param growth how many blocks the edge grows each tick it walks; zero or less reaches the range at once
     */
    public WispFlood(BlockPos origin, double range, double growth) {
        this(origin, range, growth, LIT_STEPS, EVERY_DARK_CELL);
    }

    /**
     * Starts a flood at a cell, its wisps' light reaching a given number of
     * steps, each dark, sighted cell taking a wisp by a chance.
     *
     * @param origin   the holder's eye cell
     * @param range    how far from the origin a cell may be, in blocks
     * @param growth   how many blocks the edge grows each tick it walks; zero or less reaches the range at once
     * @param litSteps how many steps out a placed wisp's light keeps a cell lit
     * @param density  the chance, from 0 to 1, that a dark, sighted cell the walk reaches takes a wisp
     */
    public WispFlood(BlockPos origin, double range, double growth, int litSteps, double density) {
        this.origin = origin.immutable();
        this.range = range;
        this.growth = growth;
        this.litSteps = litSteps;
        this.density = density;
        frontier.add(this.origin);
        visited.add(this.origin.asLong());
    }

    /**
     * The flood from where the holder's eyes moved to: walked afresh from
     * the new cell, nearest first, but reaching as far as this one had
     * grown and reckoning the wisps this one placed, so a holder walking
     * keeps lighting the dark air around them rather than waiting on a
     * fresh edge to grow past the cells already lit.
     * operator UAT 2026-10-10: Radiant placed nothing while the holder walked, and caught up only once they stopped
     *
     * @param eyes the holder's eye cell now
     * @return the flood from there
     */
    public WispFlood movedTo(BlockPos eyes) {
        WispFlood moved = new WispFlood(eyes, range, growth, litSteps, density);
        moved.edge = edge;
        // a wisp left past the new flood's reach lights none of its cells, so the walk forgets it
        double kept = range + litSteps;
        placed.stream().filter(wisp -> wisp.distSqr(eyes) <= kept * kept).forEach(moved.placed::add);
        moved.lastWalked = lastWalked;
        return moved;
    }

    /**
     * The cell the flood started at.
     *
     * @return the origin
     */
    public BlockPos origin() {
        return origin;
    }

    /**
     * Notes the game tick the flood last walked on.
     *
     * @param gameTime the game tick
     */
    public void walkedAt(long gameTime) {
        lastWalked = gameTime;
    }

    /**
     * The game tick the flood last walked on.
     *
     * @return the game tick
     */
    public long lastWalked() {
        return lastWalked;
    }

    /**
     * The wisps this flood has placed.
     *
     * @return the placed cells
     */
    public List<BlockPos> placed() {
        return List.copyOf(placed);
    }

    /**
     * Walks up to a budget of cells, nearest first, placing a wisp in each
     * cell that takes one.
     *
     * @param budget how many cells to walk
     * @param cells  what the world says of each cell
     * @param random the roll each dark, sighted cell takes against the density
     * @return how many wisps it placed
     */
    public int walk(int budget, Cells cells, RandomSource random) {
        edge = growth <= 0 ? range : Math.min(range, edge + growth);
        int placedNow = 0;
        for (int walked = 0; walked < budget && withinTheEdge(frontier.peek()); walked++) {
            BlockPos cell = frontier.poll();
            if (takes(cell, cells, random)) {
                cells.place(cell);
                placed.add(cell);
                placedNow++;
            }
            spread(cell, cells);
        }
        return placedNow;
    }

    /**
     * Whether a frontier cell lies within the edge, so this tick may walk it.
     *
     * @param cell the frontier's next cell, or null when it is spent
     * @return true when there is a cell and the edge has reached it
     */
    private boolean withinTheEdge(@Nullable BlockPos cell) {
        return cell != null && cell.distSqr(origin) <= edge * edge;
    }

    /**
     * How far the edge has grown, in blocks.
     *
     * @return the edge's radius
     */
    double edge() {
        return edge;
    }

    private boolean takes(BlockPos cell, Cells cells, RandomSource random) {
        return cell.distManhattan(origin) > CLEAR_OF_THE_EYES && !litByThisFlood(cell)
                && picks(cell, cells, random, density);
    }

    /**
     * Whether a cell the walk reaches is picked for a wisp: dark, a roll
     * under the density, and in the holder's sight, the sight asked last
     * since it is the dearest to answer.
     *
     * @param cell    the cell
     * @param cells   what the world says of the cell
     * @param random  the roll
     * @param density the chance, from 0 to 1, that a dark, sighted cell is picked
     * @return true when the cell takes a wisp
     */
    static boolean picks(BlockPos cell, Cells cells, RandomSource random, double density) {
        return cells.dark(cell) && random.nextDouble() < density && cells.seen(cell);
    }

    private void spread(BlockPos cell, Cells cells) {
        for (Direction direction : Direction.values()) {
            BlockPos next = cell.relative(direction);
            if (visited.size() < MOST_VISITED && inRange(next) && cells.open(next) && visited.add(next.asLong())) {
                frontier.add(next);
            }
        }
    }

    private boolean inRange(BlockPos cell) {
        return cell.distSqr(origin) <= range * range;
    }

    /**
     * Whether a wisp this flood placed lights a cell: the world's light
     * catches up only once the tick ends, so the flood reckons its own.
     *
     * @param cell the cell
     * @return true within a placed wisp's lit steps
     */
    boolean litByThisFlood(BlockPos cell) {
        for (BlockPos wisp : placed) {
            if (wisp.distManhattan(cell) <= litSteps) {
                return true;
            }
        }
        return false;
    }

    /** What the world says of a cell, as the flood asks it. */
    public interface Cells {
        /**
         * Whether the flood may pass through a cell: open air or a wisp.
         *
         * @param cell the cell
         * @return true when open
         */
        boolean open(BlockPos cell);

        /**
         * Whether a cell is open air under the light threshold.
         *
         * @param cell the cell
         * @return true when it may take a wisp
         */
        boolean dark(BlockPos cell);

        /**
         * Whether the holder's eyes see a cell.
         *
         * @param cell the cell
         * @return true when in sight
         */
        boolean seen(BlockPos cell);

        /**
         * Places a wisp in a cell.
         *
         * @param cell the cell
         */
        void place(BlockPos cell);
    }
}
