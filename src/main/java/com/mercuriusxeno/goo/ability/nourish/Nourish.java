package com.mercuriusxeno.goo.ability.nourish;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The nourishment standing on a player: a food point every interval until
 * it expires. Times are absolute game times, so the state changes, and
 * saves, only when a point lands.
 * nourish-restores-hunger-over-time
 *
 * @param interval  the ticks between food points
 * @param nextAt    the game time the next food point lands at
 * @param expiresAt the game time the nourishment ends at; zero when none stands
 */
public record Nourish(int interval, long nextAt, long expiresAt) {

    /** The nourishment a player without Nourish holds. */
    public static final Nourish NONE = new Nourish(1, 0L, 0L);
    /**
     * The expiry of nourishment the glove holds, standing until the held effect ends.
     * self-effects-trickle-until-ended
     */
    public static final long NEVER_EXPIRES = Long.MAX_VALUE;

    private static final String FIELD_INTERVAL = "interval";
    private static final String FIELD_NEXT_AT = "next_at";
    private static final String FIELD_EXPIRES_AT = "expires_at";

    /** Codec for the saved nourishment. */
    public static final MapCodec<Nourish> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.INT.fieldOf(FIELD_INTERVAL).forGetter(Nourish::interval),
            Codec.LONG.fieldOf(FIELD_NEXT_AT).forGetter(Nourish::nextAt),
            Codec.LONG.fieldOf(FIELD_EXPIRES_AT).forGetter(Nourish::expiresAt)
    ).apply(inst, Nourish::new));

    /**
     * Answers whether nourishment stands.
     *
     * @return true while Nourish holds
     */
    public boolean stands() {
        return expiresAt > 0L;
    }

    /**
     * Applies Nourish: standing nourishment stacks the duration onto its
     * expiry and keeps its clock; otherwise the first point lands an
     * interval from now.
     *
     * @param pointInterval the ticks between food points
     * @param duration      the ticks the nourishment lasts
     * @param now           the game time
     * @return the nourishment after the invoke
     */
    public Nourish apply(int pointInterval, int duration, long now) {
        if (stands()) {
            return new Nourish(interval, nextAt, expiresAt == NEVER_EXPIRES ? NEVER_EXPIRES : expiresAt + duration);
        }
        return new Nourish(pointInterval, now + pointInterval, now + duration);
    }

    /**
     * Starts nourishment the glove holds: no expiry, the first point an
     * interval from now; standing nourishment keeps its clock and holds on.
     * self-effects-trickle-until-ended
     *
     * @param pointInterval the ticks between food points
     * @param now           the game time
     * @return the nourishment after the start
     */
    public Nourish hold(int pointInterval, long now) {
        return stands() ? new Nourish(interval, nextAt, NEVER_EXPIRES)
                : new Nourish(pointInterval, now + pointInterval, NEVER_EXPIRES);
    }

    /**
     * Answers whether a food point lands this tick: one stands, its time has
     * come and it falls inside the nourishment.
     *
     * @param now the game time
     * @return true when the player gains a food point now
     */
    public boolean feeds(long now) {
        return stands() && now >= nextAt && nextAt <= expiresAt;
    }

    /**
     * Advances the nourishment one tick: it ends at its expiry, and a point
     * landing schedules the next an interval on.
     *
     * @param now the game time
     * @return the nourishment after the tick, the same instance when nothing changed
     */
    public Nourish tick(long now) {
        if (!stands()) {
            return this;
        }
        if (feeds(now)) {
            return new Nourish(interval, nextAt + interval, expiresAt);
        }
        return now >= expiresAt ? NONE : this;
    }
}
