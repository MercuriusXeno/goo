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

    /**
     * A room's extent as it is laid: the 9 by 9 around the center voidstone,
     * wide enough that the copies tiled to render distance stay few.
     * decision outsider-room-starts-nine-by-nine
     */
    public static final double LAID = 4.5;
    /** From a block's corner to its middle, and from a whole-block mark to the next ring's middle. */
    private static final double HALF_BLOCK = 0.5;
    /** Rings out from the center across a square: R each way and the center itself. */
    private static final int SIDES_PER_SQUARE = 2;
    /** The void (mB) within which a feed counts as landing on a whole-block mark: far under any real feed. */
    private static final double MARK_SNAP = 1.0E-6;

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
     * A room's boundary in world blocks: its center voidstone's column and
     * its extent, which the server's build test and the client's star test
     * both read, so the blocks a build refuses at the boundary are the blocks
     * drawn without stars.
     * decision grown-extent-is-the-one-wrap-and-build-boundary
     * decision boundary-cut-voidstone-draws-without-its-stars
     *
     * @param centerX the center voidstone's x
     * @param centerZ the center voidstone's z
     * @param extent  the room's extent
     */
    public record Bounds(int centerX, int centerZ, double extent) {

        /**
         * Whether a block's column lies wholly inside the boundary.
         *
         * @param x the block's x
         * @param z the block's z
         * @return true where the block's whole footprint stands inside
         */
        public boolean holdsWholeBlock(int x, int z) {
            return RoomExtent.holdsWholeBlock(extent, x - centerX, z - centerZ);
        }
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
            double cost = ringPrice(ring, base, perDistance);
            double mark = ring + HALF_BLOCK;
            double toMark = (mark - grown) * cost;
            // A feed short of the mark by rounding alone lands on it, so the next feed starts the next ring.
            if (left < toMark - MARK_SNAP) {
                return grown + left / cost;
            }
            left -= toMark;
            grown = mark;
            if (left < MARK_SNAP) {
                return grown;
            }
        }
        return grown;
    }

    /**
     * The void a tick of the hold feeds: the share of the growing ring's
     * price the ring time owes this tick, so a hold starting on a whole-block
     * mark reaches the next one after the ring time, having fed that ring's
     * price and no more. The ring's price fed so far gives the ticks held
     * into it; the feed tops it up to what one tick more owes, rounded up.
     * decision outsider-room-grows-one-block-a-second-on-every-side
     *
     * @param extent      the room's extent
     * @param base        the void a cell costs at the center (mB)
     * @param perDistance the void each block of Manhattan distance adds (mB)
     * @param ringTicks   the ticks every ring takes
     * @return the feed (mB)
     */
    public static long feedFor(double extent, int base, int perDistance, int ringTicks) {
        int ring = ringGrowing(extent);
        long price = ringPrice(ring, base, perDistance);
        long fed = Math.min(price - 1, Math.round((extent - (ring - HALF_BLOCK)) * price));
        long ticksHeld = fed * ringTicks / price;
        long owed = Math.ceilDiv(price * (ticksHeld + 1), ringTicks);
        return Math.max(1, owed - fed);
    }

    /**
     * The price of the ring an extent is growing through, which a ring time of the hold feeds.
     * decision outsider-petal-reads-rate-and-time-in-the-room
     *
     * @param extent      the room's extent
     * @param base        the void a cell costs at the center (mB)
     * @param perDistance the void each block of Manhattan distance adds (mB)
     * @return the ring's price (mB)
     */
    public static long priceOfRingGrowing(double extent, int base, int perDistance) {
        return ringPrice(ringGrowing(extent), base, perDistance);
    }

    /**
     * The void growing through a ring costs, never less than 1 mB, so
     * every feed moves the extent.
     *
     * @param ring        the ring
     * @param base        the void a cell costs at the center (mB)
     * @param perDistance the void each block of Manhattan distance adds (mB)
     * @return the ring's price (mB)
     */
    private static long ringPrice(int ring, int base, int perDistance) {
        return Math.max(1, PlatformSpiral.ringCost(ring, base, perDistance));
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
