package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

/**
 * Radiant's flood: from the holder's eye cell outward through connected
 * open air, nearest cells first, a wisp in each dark cell the holder sees
 * and that no wisp this flood placed already lights. Each tick walks a
 * budget of cells, so the space right around the holder lights within a
 * tick or two and the lit zone keeps pushing out while the channel holds,
 * up to its range or the bounds of its visited cells.
 * decision radiant-wisps-where-light-is-low
 * operator ruling 2026-10-10: wisps flood out from the eyes very fast, nearest first, to 64 blocks
 */
public final class WispFlood {

    /**
     * How far a fresh wisp's light reaches before a cell reads dark again:
     * a wisp gives 15, the threshold sits at 10, and light drops one a step,
     * so a cell five steps out still reads 10, lit.
     */
    static final int LIT_STEPS = 5;
    /** How close to the eyes a wisp may sit, in steps: none in the holder's face. */
    static final int CLEAR_OF_THE_EYES = 2;
    /** The most cells one flood remembers, so open sky never grows it without end. */
    static final int MOST_VISITED = 300_000;

    private final BlockPos origin;
    private final double range;
    private final Deque<BlockPos> frontier = new ArrayDeque<>();
    private final LongOpenHashSet visited = new LongOpenHashSet();
    private final List<BlockPos> placed = new ArrayList<>();
    private long lastWalked;

    /**
     * Starts a flood at a cell.
     *
     * @param origin the holder's eye cell
     * @param range  how far from the origin a cell may be, in blocks
     */
    public WispFlood(BlockPos origin, double range) {
        this.origin = origin.immutable();
        this.range = range;
        frontier.add(this.origin);
        visited.add(this.origin.asLong());
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
     * @return how many wisps it placed
     */
    public int walk(int budget, Cells cells) {
        int placedNow = 0;
        for (int walked = 0; walked < budget && !frontier.isEmpty(); walked++) {
            BlockPos cell = frontier.poll();
            if (takes(cell, cells)) {
                cells.place(cell);
                placed.add(cell);
                placedNow++;
            }
            spread(cell, cells);
        }
        return placedNow;
    }

    private boolean takes(BlockPos cell, Cells cells) {
        return cell.distManhattan(origin) > CLEAR_OF_THE_EYES && cells.dark(cell) && !litByThisFlood(cell)
                && cells.seen(cell);
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
            if (wisp.distManhattan(cell) <= LIT_STEPS) {
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
