package com.mercuriusxeno.goo.block.fluid;

import com.mercuriusxeno.goo.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

/**
 * The goo stream pouring into a container: the type and volume of the last
 * tick that landed goo, answered for a short hold after that tick so a volume
 * landing in one tick still reads as a pour (decision diagnose-then-fix-vat-stream-flash).
 */
public final class GooStream {

    /**
     * Ticks the stream keeps answering after the last tick that landed goo.
     */
    public static final long HOLD_TICKS = 10L;

    private @Nullable ResourceKey<GooTypeDefinition> type;
    private int rate;
    private long tick = -1;

    /**
     * Records goo landing at a tick. Volumes landing in the same tick add up;
     * the first volume of a later tick starts the rate over.
     *
     * @param landedType the goo type that landed
     * @param volume     the volume that landed in mB
     * @param now        the tick it landed
     */
    public void record(ResourceKey<GooTypeDefinition> landedType, int volume, long now) {
        if (now != tick) {
            rate = 0;
            tick = now;
        }
        type = landedType;
        rate += volume;
    }

    /**
     * Returns the streaming type while the hold lasts, or null once it has passed.
     *
     * @param currentTick the current game tick
     * @return the streaming goo type, or null
     */
    public @Nullable ResourceKey<GooTypeDefinition> typeAt(long currentTick) {
        return holds(currentTick, tick) ? type : null;
    }

    /**
     * Returns the volume the last landing tick carried while the hold lasts, or 0 once it has passed.
     *
     * @param currentTick the current game tick
     * @return the stream rate in mB, or 0
     */
    public int rateAt(long currentTick) {
        return holds(currentTick, tick) ? rate : 0;
    }

    /**
     * Returns the last tick goo landed, or -1 before any has.
     *
     * @return the last landing tick
     */
    public long lastTick() {
        return tick;
    }

    /**
     * Whether a stream last fed at one tick still shows at another.
     *
     * @param currentTick the current game tick
     * @param streamTick  the tick goo last landed
     * @return true while the hold lasts
     */
    public static boolean holds(long currentTick, long streamTick) {
        return currentTick - streamTick <= HOLD_TICKS;
    }
}
