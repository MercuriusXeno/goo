package com.mercuriusxeno.goo.ability.pocket;

import java.util.List;

/**
 * The void goo Pocket holds back for the items stored in it: a flat amount
 * for each slot they fill, a slot holding one stack's worth of an item as a
 * chest counts it, as the operator ruled. The reserve follows each entry's
 * slots alone, so taking items back releases what storing them reserved.
 * decision pocket-is-a-void-reserving-container-with-scroll-and-search
 */
public final class PocketReserve {

    private PocketReserve() {
    }

    /**
     * The slots a count of one item fills.
     *
     * @param count     the items
     * @param stackSize the most one slot of the item holds
     * @return the slots, rounded up
     */
    static long slotsOf(long count, int stackSize) {
        long perSlot = Math.max(1, stackSize);
        return (Math.max(0, count) + perSlot - 1) / perSlot;
    }

    /**
     * The void storing more of an item reserves: the slot cost for each new slot it fills.
     *
     * @param held      the entry's count before
     * @param adding    the items stored
     * @param stackSize the most one slot of the item holds
     * @param slotCost  the void each slot reserves
     * @return the void reserved
     */
    public static long reserveToStore(long held, long adding, int stackSize, int slotCost) {
        return (slotsOf(held + adding, stackSize) - slotsOf(held, stackSize)) * slotCost;
    }

    /**
     * The void taking items back releases: the slot cost for each slot it empties.
     *
     * @param held      the entry's count before
     * @param taking    the items taken
     * @param stackSize the most one slot of the item holds
     * @param slotCost  the void each slot reserves
     * @return the void released
     */
    public static long releaseOnTake(long held, long taking, int stackSize, int slotCost) {
        return (slotsOf(held, stackSize) - slotsOf(held - taking, stackSize)) * slotCost;
    }

    /**
     * The most of an offered stack the void at hand can store.
     *
     * @param held          the entry's count before
     * @param offered       the items offered
     * @param stackSize     the most one slot of the item holds
     * @param slotCost      the void each slot reserves
     * @param voidAvailable the void at hand
     * @return the items storable, at most the offer
     */
    public static long mostStorable(long held, long offered, int stackSize, int slotCost, long voidAvailable) {
        long perSlot = Math.max(1, stackSize);
        long room = slotsOf(held, stackSize) * perSlot - held;
        long newSlots = slotCost <= 0 ? Long.MAX_VALUE : voidAvailable / slotCost;
        long fits = newSlots >= Long.MAX_VALUE / perSlot ? Long.MAX_VALUE : room + newSlots * perSlot;
        return Math.min(offered, fits);
    }

    /**
     * The void a shelf holds back, which Pocket's screen shows.
     *
     * @param shelf    the pocket's entries
     * @param slotCost the void each slot reserves
     * @return the void reserved
     */
    public static long voidHeldBy(List<PocketEntry> shelf, int slotCost) {
        return shelf.stream().mapToLong(entry -> slotsOf(entry.count(), entry.kind().getMaxStackSize())).sum()
                * slotCost;
    }
}
