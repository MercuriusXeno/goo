package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

/**
 * A bare prism's charge toward the combo a channeled stream converts it into:
 * each tick a stream of one type holds on it adds one, and the prism converts
 * once the charge reaches the hold. A break, the stream released or looking
 * away for longer than a dropped tick, or a stream of another type starts the
 * charge again, so only a stream held until it converts fully takes the
 * prism; the network's jitter, two hold ticks landing in one server tick or
 * one tick dropped, breaks nothing.
 * channeled-hosts-convert-prisms-over-the-hold
 *
 * @param type     the type of the stream charging the prism, null before any charge
 * @param charge   the ticks the stream has held on the prism without a break
 * @param lastTick the game time of the last tick that charged it
 */
public record PrismConversion(@Nullable ResourceKey<GooTypeDefinition> type, int charge, long lastTick) {

    /** A prism no stream has charged. */
    public static final PrismConversion NONE = new PrismConversion(null, 0, Long.MIN_VALUE);
    /** The longest gap, in ticks, between two charging ticks that still carries the charge on. */
    static final int LONGEST_GAP = 2;

    /**
     * The charge after one tick of a stream holding on the prism.
     *
     * @param streamType the type of the stream holding on the prism
     * @param now        the game time of this tick
     * @return the charge: unchanged for a repeat in the same tick, one more where the same type held on the
     *         prism within the longest gap, else one
     */
    public PrismConversion chargedBy(ResourceKey<GooTypeDefinition> streamType, long now) {
        if (!streamType.equals(type)) {
            return new PrismConversion(streamType, 1, now);
        }
        long gap = now - lastTick;
        if (gap == 0) {
            return this;
        }
        return new PrismConversion(streamType, gap > 0 && gap <= LONGEST_GAP ? charge + 1 : 1, now);
    }

    /**
     * Whether the charge has reached the hold, which converts the prism.
     *
     * @param hold the ticks of unbroken stream the conversion takes
     * @return true at or past the hold
     */
    public boolean converts(int hold) {
        return charge >= hold;
    }

    /**
     * The share of the hold the charge has reached, which the prism's
     * charging look shows.
     *
     * @param hold the ticks of unbroken stream the conversion takes
     * @return the charge over the hold, at most 1
     */
    public float share(int hold) {
        return Math.min(1f, charge / (float) hold);
    }
}
