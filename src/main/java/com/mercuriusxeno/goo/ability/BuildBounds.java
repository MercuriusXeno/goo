package com.mercuriusxeno.goo.ability;

import net.minecraft.core.BlockPos;

/**
 * How far one Build reaches: the widest its footprint runs on either side and
 * the highest it rises, tunables on the ability's build step that the drag,
 * the rise, the preview and the server's check all read.
 * decision build-dimensions-reach-sixteen-by-sixteen-by-sixteen
 *
 * @param maxSpan the widest a footprint runs on either axis, in blocks
 * @param maxRise the highest a Build rises, in blocks
 */
public record BuildBounds(int maxSpan, int maxRise) {

    /** The bounds a build step reads when its file names none: sixteen by sixteen by sixteen. */
    public static final BuildBounds SIXTEEN_CUBED = new BuildBounds(16, 16);
    /** The lowest rise a cast makes, however little the pitch moved. */
    public static final int MIN_RISE = 1;

    /**
     * A footprint the caster asked for, cut to the bounds: the opposite corner
     * pulled within the span of the pinned one and onto its level, the rise
     * held between the lowest and the highest.
     *
     * @param corner  the pinned ground cell
     * @param dragged the ground cell the drag reached
     * @param rise    the rise asked for
     * @return the capped footprint
     */
    public BuildFootprint cap(BlockPos corner, BlockPos dragged, int rise) {
        BlockPos opposite = new BlockPos(clampSpan(corner.getX(), dragged.getX()), corner.getY(),
                clampSpan(corner.getZ(), dragged.getZ()));
        return new BuildFootprint(corner.immutable(), opposite, capRise(rise));
    }

    /**
     * A rise held between the lowest and the highest.
     *
     * @param rise the rise asked for
     * @return the rise within the bounds
     */
    public int capRise(long rise) {
        return (int) Math.clamp(rise, MIN_RISE, maxRise);
    }

    /**
     * Whether a footprint as sent stands within the bounds, the check the
     * server makes before it grows anything.
     *
     * @param footprint the footprint as sent
     * @return true for a footprint on one level within the span and the rise
     */
    public boolean admits(BuildFootprint footprint) {
        return spansWithin(footprint.corner(), footprint.opposite()) && footprint.rise() == capRise(footprint.rise());
    }

    private boolean spansWithin(BlockPos corner, BlockPos opposite) {
        return corner.getY() == opposite.getY() && span(corner.getX(), opposite.getX()) <= maxSpan
                && span(corner.getZ(), opposite.getZ()) <= maxSpan;
    }

    private int clampSpan(int from, int to) {
        return Math.clamp(to, from - (maxSpan - 1), from + (maxSpan - 1));
    }

    private static int span(int from, int to) {
        return Math.abs(to - from) + 1;
    }
}
