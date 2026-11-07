package com.mercuriusxeno.goo.ability.pocket;

import com.mercuriusxeno.goo.item.ContainerCapacity;
import java.util.List;

/**
 * The void goo Pocket holds back for the items stored in it, on the scale
 * void grows a vat or canister: each item takes the room of one bucket, and
 * the pocket reserves the least void whose growth holds that room, so the
 * n-th mB reserved holds n+1 mB and every stack reserves less than the one
 * before. The reserve follows the count held alone, so taking items back
 * releases what storing them reserved.
 * decision pocket-is-a-void-reserving-container-with-scroll-and-search
 * decision each-void-mb-adds-n-plus-one-capacity
 */
public final class PocketReserve {

    /** The room one stored item takes, in mB of vat capacity: a bucket. */
    public static final long MB_PER_ITEM = 1000;
    /** Void v grows about v squared over two, so the reserve for some room starts near the root of twice it. */
    private static final double GROWTH_INVERSE = 2.0;

    private PocketReserve() {
    }

    /**
     * The void a pocket holding some items reserves: the least void whose
     * growth holds a bucket of room per item.
     *
     * @param items the items held
     * @return the void reserved (mB), 0 for an empty pocket
     */
    public static long voidFor(long items) {
        long room = Math.max(0, items) * MB_PER_ITEM;
        long reserved = (long) Math.ceil(Math.sqrt(GROWTH_INVERSE * room));
        while (reserved > 0 && ContainerCapacity.voidGrowth(reserved - 1) >= room) {
            reserved--;
        }
        while (ContainerCapacity.voidGrowth(reserved) < room) {
            reserved++;
        }
        return reserved;
    }

    /**
     * The void a shelf holds back, which Pocket's screen shows.
     *
     * @param shelf the pocket's entries
     * @return the void reserved (mB)
     */
    public static long voidHeldBy(List<PocketEntry> shelf) {
        return voidFor(shelf.stream().mapToLong(PocketEntry::count).sum());
    }

    /**
     * The void storing more items reserves on top of what the pocket holds.
     *
     * @param held   the items held before
     * @param adding the items stored
     * @return the void reserved (mB)
     */
    public static long reserveToStore(long held, long adding) {
        return voidFor(held + adding) - voidFor(held);
    }

    /**
     * The void taking items back releases.
     *
     * @param held   the items held before
     * @param taking the items taken
     * @return the void released (mB)
     */
    public static long releaseOnTake(long held, long taking) {
        return voidFor(held) - voidFor(held - taking);
    }

    /**
     * The most of an offered stack the void at hand can store.
     *
     * @param held          the items held before
     * @param offered       the items offered
     * @param voidAvailable the void the player can reserve (mB)
     * @return the items storable, at most the offer
     */
    public static long mostStorable(long held, long offered, long voidAvailable) {
        long fits = 0;
        long overflows = offered + 1;
        while (overflows - fits > 1) {
            long middle = (fits + overflows) >>> 1;
            if (reserveToStore(held, middle) <= voidAvailable) {
                fits = middle;
            } else {
                overflows = middle;
            }
        }
        return fits;
    }
}
