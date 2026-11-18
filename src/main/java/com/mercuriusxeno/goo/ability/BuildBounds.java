package com.mercuriusxeno.goo.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

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
     * A footprint on the ground the caster asked for, cut to the bounds.
     *
     * @param corner  the pinned ground cell
     * @param dragged the ground cell the drag reached
     * @param rise    the rise asked for
     * @return the capped footprint, rising up
     */
    public BuildFootprint cap(BlockPos corner, BlockPos dragged, int rise) {
        return cap(corner, dragged, rise, Direction.UP);
    }

    /**
     * A footprint the caster asked for, cut to the bounds: the opposite corner
     * pulled within the span of the pinned one and into the plane of the face
     * it was pinned on, the rise held between the lowest and the highest.
     * decision build-goes-sideways
     *
     * @param corner  the pinned ground cell
     * @param dragged the ground cell the drag reached
     * @param rise    the rise asked for
     * @param face    the face the corner was pinned on
     * @return the capped footprint
     */
    public BuildFootprint cap(BlockPos corner, BlockPos dragged, int rise, Direction face) {
        Direction.Axis normal = face.getAxis();
        int[] opposite = new int[Direction.Axis.VALUES.length];
        for (Direction.Axis axis : Direction.Axis.VALUES) {
            int from = corner.get(axis);
            opposite[axis.ordinal()] = axis == normal ? from : clampSpan(from, dragged.get(axis));
        }
        return new BuildFootprint(corner.immutable(), new BlockPos(opposite[Direction.Axis.X.ordinal()],
                opposite[Direction.Axis.Y.ordinal()], opposite[Direction.Axis.Z.ordinal()]),
                capRise(rise), face);
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
     * @return true for a footprint in its face's plane within the span and the rise
     */
    public boolean admits(BuildFootprint footprint) {
        return spansWithin(footprint) && footprint.rise() == capRise(footprint.rise());
    }

    private boolean spansWithin(BuildFootprint footprint) {
        Direction.Axis normal = footprint.face().getAxis();
        for (Direction.Axis axis : Direction.Axis.VALUES) {
            int from = footprint.corner().get(axis);
            int to = footprint.opposite().get(axis);
            if (axis == normal ? from != to : span(from, to) > maxSpan) {
                return false;
            }
        }
        return true;
    }

    private int clampSpan(int from, int to) {
        return Math.clamp(to, from - (maxSpan - 1), from + (maxSpan - 1));
    }

    private static int span(int from, int to) {
        return Math.abs(to - from) + 1;
    }
}
