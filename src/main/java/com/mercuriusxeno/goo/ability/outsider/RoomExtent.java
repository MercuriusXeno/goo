package com.mercuriusxeno.goo.ability.outsider;

import net.minecraft.core.BlockPos;

/**
 * An Outsider room's extent: one distance in blocks from the middle of the
 * room's center voidstone to its boundary, the same east, west, south and
 * north, so the room grows outward on all sides at once. Void fed raises it
 * continuously, by the share of a block the feed covers: growing it through
 * ring R, the blocks R out, costs what that ring of the old spiral cost, so
 * the hold's pace and the ability's params keep their meaning. A whole-block
 * mark stands at every half block, where a ring of blocks lies wholly inside,
 * and only a block lying wholly inside takes a build.
 * decision outsider-room-grows-fractionally-on-all-sides-at-once
 * decision grown-extent-is-the-one-wrap-and-build-boundary
 */
public final class RoomExtent {

    /** A room's extent as it is laid: the 3 by 3 around the center voidstone. */
    public static final double LAID = 1.5;
    /** From a block's corner to its middle, and from a whole-block mark to the next ring's middle. */
    private static final double HALF_BLOCK = 0.5;
    /** Rings out from the center across a square: R each way and the center itself. */
    private static final int SIDES_PER_SQUARE = 2;

    private RoomExtent() {
    }

    /**
     * The four planes a room's boundary stands on, in world coordinates.
     *
     * @param west  the west plane's x
     * @param east  the east plane's x
     * @param north the north plane's z
     * @param south the south plane's z
     */
    public record Planes(double west, double east, double north, double south) {
    }

    /**
     * The planes an extent stands on around a room's center voidstone.
     *
     * @param center the room's center voidstone
     * @param extent the room's extent
     * @return the boundary's planes
     */
    public static Planes planesAround(BlockPos center, double extent) {
        double x = center.getX() + HALF_BLOCK;
        double z = center.getZ() + HALF_BLOCK;
        return new Planes(x - extent, x + extent, z - extent, z + extent);
    }

    /**
     * The ring an extent is growing through: the blocks it cuts, or the next
     * ring out where it stands on a whole-block mark.
     *
     * @param extent the room's extent
     * @return the ring
     */
    static int ringGrowing(double extent) {
        return (int) Math.floor(extent + HALF_BLOCK);
    }

    /**
     * The rings lying wholly inside an extent, the center counting as ring 0.
     *
     * @param extent the room's extent
     * @return the outermost whole ring
     */
    public static int wholeRings(double extent) {
        return (int) Math.floor(extent - HALF_BLOCK);
    }

    /**
     * The cells of the square an extent holds whole, for the edges that still
     * stand on block faces.
     *
     * @param extent the room's extent
     * @return the whole square's cell count
     */
    public static int wholeCells(double extent) {
        int across = SIDES_PER_SQUARE * wholeRings(extent) + 1;
        return across * across;
    }

    /**
     * The extent after a feed: each ring takes the share of the feed that
     * finishes it at its own cost, and what is left carries to the next.
     *
     * @param extent      the room's extent before the feed
     * @param feed        the void fed (mB)
     * @param base        the void a cell costs at the center (mB)
     * @param perDistance the void each block of Manhattan distance adds (mB)
     * @return the extent after it
     */
    public static double grown(double extent, long feed, int base, int perDistance) {
        double grown = extent;
        double left = feed;
        while (left > 0) {
            int ring = ringGrowing(grown);
            double cost = Math.max(1, PlatformSpiral.ringCost(ring, base, perDistance));
            double mark = ring + HALF_BLOCK;
            double toMark = (mark - grown) * cost;
            if (left < toMark) {
                return grown + left / cost;
            }
            left -= toMark;
            grown = mark;
        }
        return grown;
    }

    /**
     * The void a tick of the hold feeds: the cost of the ring the extent is
     * growing through spread over the ring time, rounded up so the ring
     * finishes within it.
     *
     * @param extent      the room's extent
     * @param base        the void a cell costs at the center (mB)
     * @param perDistance the void each block of Manhattan distance adds (mB)
     * @param ringTicks   the ticks every ring takes
     * @return the feed (mB)
     */
    public static long feedFor(double extent, int base, int perDistance, int ringTicks) {
        long ringCost = PlatformSpiral.ringCost(ringGrowing(extent), base, perDistance);
        return (ringCost + ringTicks - 1) / ringTicks;
    }

    /**
     * Whether a block lies wholly inside an extent, the only blocks a build may take.
     *
     * @param extent the room's extent
     * @param east   the block's offset east of the center voidstone
     * @param south  the block's offset south of it
     * @return true where the block's whole footprint stands inside
     */
    public static boolean holdsWholeBlock(double extent, int east, int south) {
        return Math.max(Math.abs(east), Math.abs(south)) + HALF_BLOCK <= extent;
    }

    /**
     * The extent of the square a platform saved as a spiral cell count
     * completed, so a room saved before the extent loses no ground.
     *
     * @param cells the saved cell count
     * @return the completed square's extent
     */
    static double ofCompletedSquare(int cells) {
        return Math.max(0, PlatformSpiral.ringOf(cells) - 1) + HALF_BLOCK;
    }
}
