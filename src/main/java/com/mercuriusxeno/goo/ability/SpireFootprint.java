package com.mercuriusxeno.goo.ability;

import net.minecraft.core.BlockPos;
import java.util.ArrayList;
import java.util.List;

/**
 * The ground a Spire rips up: a rectangle of ground cells on the pinned
 * corner's level, the drag sizing it, and the rise the pitch sets, each
 * capped so one cast stays a wall or a platform, never a landslide.
 * decision spire-rips-walls-and-platforms
 *
 * @param corner   the ground cell the press pinned
 * @param opposite the ground cell across the footprint, on the corner's level
 * @param rise     how many blocks the ground lifts
 */
public record SpireFootprint(BlockPos corner, BlockPos opposite, int rise) {

    /** The widest a footprint runs on either axis, in blocks. */
    public static final int MAX_SPAN = 8;
    /** The highest the ground lifts, in blocks. */
    public static final int MAX_RISE = 8;
    /** The lowest rise a cast makes, however little the pitch moved. */
    public static final int MIN_RISE = 1;

    /**
     * A footprint the caster asked for, cut to the caps: the opposite corner
     * pulled within the span of the pinned one and onto its level, the rise
     * held between the lowest and the highest.
     *
     * @param corner   the pinned ground cell
     * @param dragged  the ground cell the drag reached
     * @param rise     the rise asked for
     * @return the capped footprint
     */
    public static SpireFootprint capped(BlockPos corner, BlockPos dragged, int rise) {
        BlockPos opposite = new BlockPos(clampSpan(corner.getX(), dragged.getX()), corner.getY(),
                clampSpan(corner.getZ(), dragged.getZ()));
        return new SpireFootprint(corner.immutable(), opposite, Math.clamp(rise, MIN_RISE, MAX_RISE));
    }

    /**
     * Whether a footprint as sent stands within the caps, the check the server
     * makes before it lifts anything.
     *
     * @return true for a footprint on one level within the span and rise caps
     */
    public boolean withinCaps() {
        return spansWithinCaps() && rise >= MIN_RISE && rise <= MAX_RISE;
    }

    /**
     * The same footprint at another rise.
     *
     * @param lower the rise
     * @return the footprint lifting that far
     */
    public SpireFootprint withRise(int lower) {
        return new SpireFootprint(corner, opposite, lower);
    }

    /**
     * The ground cells of the footprint, the top block of each column that lifts.
     *
     * @return the cells, row by row
     */
    public List<BlockPos> groundCells() {
        List<BlockPos> cells = new ArrayList<>();
        for (BlockPos cell : BlockPos.betweenClosed(corner, opposite)) {
            cells.add(cell.immutable());
        }
        return cells;
    }

    /**
     * The footprint's lowest corner, the minimum on every axis.
     *
     * @return the lowest corner
     */
    public BlockPos min() {
        return new BlockPos(Math.min(corner.getX(), opposite.getX()), corner.getY(),
                Math.min(corner.getZ(), opposite.getZ()));
    }

    /**
     * The footprint's highest corner, the maximum on every axis.
     *
     * @return the highest corner
     */
    public BlockPos max() {
        return new BlockPos(Math.max(corner.getX(), opposite.getX()), corner.getY(),
                Math.max(corner.getZ(), opposite.getZ()));
    }

    private boolean spansWithinCaps() {
        return corner.getY() == opposite.getY() && span(corner.getX(), opposite.getX()) <= MAX_SPAN
                && span(corner.getZ(), opposite.getZ()) <= MAX_SPAN;
    }

    private static int clampSpan(int from, int to) {
        return Math.clamp(to, from - (MAX_SPAN - 1), from + (MAX_SPAN - 1));
    }

    private static int span(int from, int to) {
        return Math.abs(to - from) + 1;
    }
}
