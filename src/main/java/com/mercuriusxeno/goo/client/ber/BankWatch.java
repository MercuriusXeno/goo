package com.mercuriusxeno.goo.client.ber;

import net.minecraft.core.BlockPos;
import java.util.HashMap;
import java.util.Map;

/**
 * Watches each timekeeper prism's bank on this client to tell when Tick
 * spends it: a charge lower than the last one seen marks the bank spending
 * for a moment after.
 * timekeeper-prism-banks-ticks-forward-only
 */
public final class BankWatch {

    /** The client's watch. */
    public static final BankWatch CLIENT = new BankWatch();

    /** Game ticks a bank reads as spending after its charge last fell. */
    static final int SPENDING_HOLD_TICKS = 10;

    private static final long NEVER_FELL = Long.MIN_VALUE;
    private static final Seen NEVER_SEEN = new Seen(0L, NEVER_FELL);

    /** The last charge seen and the tick it last fell, by prism. */
    private final Map<BlockPos, Seen> seen = new HashMap<>();

    /**
     * What the watch last saw of one prism.
     *
     * @param charge the charge seen
     * @param fellAt the game time the charge last fell
     */
    private record Seen(long charge, long fellAt) {
    }

    /**
     * Notes a prism's charge this frame and answers whether it is being spent.
     *
     * @param pos    the prism
     * @param charge its bank's charge
     * @param now    the game time
     * @return true when the charge fell within the last moments
     */
    public boolean spending(BlockPos pos, long charge, long now) {
        Seen last = seen.getOrDefault(pos, NEVER_SEEN);
        long fellAt = last != NEVER_SEEN && charge < last.charge() ? now : last.fellAt();
        seen.put(pos.immutable(), new Seen(charge, fellAt));
        return fellAt != NEVER_FELL && now - fellAt <= SPENDING_HOLD_TICKS;
    }
}
