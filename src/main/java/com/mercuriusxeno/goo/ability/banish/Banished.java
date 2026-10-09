package com.mercuriusxeno.goo.ability.banish;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The teleportitis curse Banish leaves on a mob, saved with it: how near a
 * player it may come before it warps, how far each warp throws it, and the
 * warps it has left before the next approach exiles it from existence.
 * Decision banish-curses-with-ender-shimmer.
 *
 * @param radius    how near a player the mob may come, in blocks, before it warps
 * @param range     the full width of a warp's random roll on each horizontal axis
 * @param warpsLeft the warps left before the next approach exiles the mob
 */
public record Banished(float radius, float range, int warpsLeft) {

    /** No curse: the mob stays where it is. */
    public static final Banished NONE = new Banished(0f, 0f, 0);

    private static final String FIELD_RADIUS = "radius";
    private static final String FIELD_RANGE = "range";
    private static final String FIELD_WARPS_LEFT = "warps_left";

    /** Saves the curse with the mob. */
    public static final MapCodec<Banished> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.FLOAT.fieldOf(FIELD_RADIUS).forGetter(Banished::radius),
            Codec.FLOAT.fieldOf(FIELD_RANGE).forGetter(Banished::range),
            Codec.INT.fieldOf(FIELD_WARPS_LEFT).forGetter(Banished::warpsLeft)
    ).apply(inst, Banished::new));

    /**
     * Whether the curse stands on the mob.
     *
     * @return true for any curse but {@link #NONE}
     */
    public boolean stands() {
        return radius > 0f;
    }

    /**
     * Whether the next approach exiles the mob rather than warping it.
     *
     * @return true with no warps left
     */
    public boolean exilesNext() {
        return warpsLeft <= 0;
    }

    /**
     * The curse after one warp spent.
     *
     * @return the curse with one warp fewer
     */
    public Banished afterWarp() {
        return new Banished(radius, range, warpsLeft - 1);
    }
}
