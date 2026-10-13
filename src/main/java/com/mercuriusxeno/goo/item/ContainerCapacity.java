package com.mercuriusxeno.goo.item;

/**
 * Pure constants and capacity formulas for vats and canisters. A vessel's
 * capacity is its base plus what the void it holds as ordinary goo adds: the
 * n-th mB of void adds n+1 mB, so v mB adds v(v+3)/2.
 *
 * decision void-goo-grows-a-container
 * decision each-void-mb-adds-n-plus-one-capacity
 */
public final class ContainerCapacity {

    /** Base canister capacity (2^20 = 1,048,576 mB). */
    public static final int CANISTER_BASE = 1 << 20;

    /** Base vat capacity (2^25 = 33,554,432 mB). */
    public static final int VAT_BASE = 1 << 25;

    private ContainerCapacity() {}

    /**
     * The capacity v mB of void adds: the n-th mB adds n+1, so the triangular
     * number of v plus one per mB, v(v+3)/2. A whole int of void squared still
     * fits a long.
     *
     * @param voidHeld the void held (mB)
     * @return the added capacity (mB), 0 for no void
     */
    public static long voidGrowth(long voidHeld) {
        long held = Math.max(0, voidHeld);
        return (held * (held + 1) >>> 1) + held;
    }

    /**
     * A vessel's capacity: its base plus the void's growth, clamped at Integer.MAX_VALUE.
     *
     * @param base     the capacity with no void (mB)
     * @param voidHeld the void held (mB)
     * @return the capacity (mB)
     */
    public static int capacity(int base, long voidHeld) {
        return (int) Math.min(Integer.MAX_VALUE, base + voidGrowth(voidHeld));
    }

    /**
     * A canister's capacity for the void it holds.
     *
     * @param voidHeld the void held (mB)
     * @return the capacity (mB), 2^20 with no void
     */
    public static int canisterCapacity(long voidHeld) {
        return capacity(CANISTER_BASE, voidHeld);
    }

    /**
     * A vat's capacity for the void it holds.
     *
     * @param voidHeld the void held (mB)
     * @return the capacity (mB), 2^25 with no void
     */
    public static int vatCapacity(long voidHeld) {
        return capacity(VAT_BASE, voidHeld);
    }

    /**
     * The most of one goo an insert may add. Any goo but void fills the
     * capacity the held void sets. Each mB of void adds at least 2 mB of
     * capacity, more than the mB it takes, so void fits until the total
     * reaches Integer.MAX_VALUE.
     *
     * @param isVoid   true when the goo inserted is void
     * @param base     the capacity with no void (mB)
     * @param total    every fluid the vessel holds together (mB)
     * @param voidHeld the void held (mB)
     * @return the room (mB), never negative
     */
    public static int room(boolean isVoid, int base, long total, long voidHeld) {
        long ceiling = isVoid ? Integer.MAX_VALUE : capacity(base, voidHeld);
        return (int) Math.max(0, ceiling - total);
    }

    /**
     * The most void a drain may take: the largest amount whose loss leaves the
     * contents within the capacity the remaining void sets. An otherwise empty
     * vessel releases its void whole; a vessel already over capacity releases none.
     *
     * @param base     the capacity with no void (mB)
     * @param total    every fluid the vessel holds together, void included (mB)
     * @param voidHeld the void held (mB)
     * @return the releasable void (mB)
     */
    public static int releasableVoid(int base, long total, long voidHeld) {
        if (voidHeld <= 0 || !fitsAfterDrain(base, total, voidHeld, 0)) {
            return 0;
        }
        // The slack rises while the capacity sits at the clamp and falls once
        // the capacity drops, so the drains that fit run from 0 to one bound.
        long fits = 0;
        long overflows = voidHeld + 1;
        while (overflows - fits > 1) {
            long middle = (fits + overflows) >>> 1;
            if (fitsAfterDrain(base, total, voidHeld, middle)) {
                fits = middle;
            } else {
                overflows = middle;
            }
        }
        return (int) fits;
    }

    /**
     * The least void that grows a base to a target capacity.
     *
     * @param base   the capacity with no void (mB)
     * @param target the capacity sought (mB)
     * @return the void needed (mB), 0 where the base already reaches it
     */
    public static int voidToReach(int base, int target) {
        if (base >= target) {
            return 0;
        }
        long fails = 0;
        long reaches = Integer.MAX_VALUE;
        while (reaches - fails > 1) {
            long middle = (fails + reaches) >>> 1;
            if (capacity(base, middle) >= target) {
                reaches = middle;
            } else {
                fails = middle;
            }
        }
        return (int) reaches;
    }

    /**
     * Whether draining some void leaves the rest within capacity.
     *
     * @param base     the capacity with no void (mB)
     * @param total    every fluid held before the drain (mB)
     * @param voidHeld the void held before the drain (mB)
     * @param drained  the void the drain takes (mB)
     * @return true when the remaining contents fit
     */
    private static boolean fitsAfterDrain(int base, long total, long voidHeld, long drained) {
        return total - drained <= capacity(base, voidHeld - drained);
    }
}
