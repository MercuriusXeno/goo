package com.mercuriusxeno.goo.ability.frost;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A mob's frozen gauge: each frost hit adds to it by the hit's share of the
 * mob's max health, a full gauge holds a long while before it thaws, and
 * the mob slows and takes more physical damage in proportion to it.
 * frozen-gauge-per-mob-encases-when-full
 *
 * @param gauge     how frozen the mob stands, from zero to FULL
 * @param holdUntil the game time a full gauge starts thawing
 * @param curve     how the gauge holds, thaws and weakens, from the last frost hit
 */
public record Frozen(float gauge, long holdUntil, FrostCurve curve) {

    /** The gauge a mob stands at before any frost reaches it. */
    public static final Frozen NONE = new Frozen(0f, 0L, FrostCurve.NONE);

    /** A full gauge: the freeze, the mob encased and still. */
    public static final float FULL = 1f;

    private static final String FIELD_GAUGE = "gauge";
    private static final String FIELD_HOLD_UNTIL = "hold_until";
    private static final String FIELD_CURVE = "curve";

    /** Codec for the saved gauge. */
    public static final MapCodec<Frozen> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.FLOAT.fieldOf(FIELD_GAUGE).forGetter(Frozen::gauge),
            Codec.LONG.fieldOf(FIELD_HOLD_UNTIL).forGetter(Frozen::holdUntil),
            FrostCurve.CODEC.codec().fieldOf(FIELD_CURVE).forGetter(Frozen::curve)
    ).apply(inst, Frozen::new));

    /** Codec for the gauge synced to the clients drawing the mob. */
    public static final StreamCodec<ByteBuf, Frozen> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, Frozen::gauge,
            ByteBufCodecs.VAR_LONG, Frozen::holdUntil,
            FrostCurve.STREAM_CODEC, Frozen::curve,
            Frozen::new);

    /**
     * The share of a gauge a frost hit adds: its amount over the mob's max
     * health, so a mob with more health freezes slower.
     *
     * @param amount    the hit's amount, in health points
     * @param maxHealth the mob's max health
     * @return the share it adds, 0 where the mob has no health
     */
    public static float shareOf(float amount, float maxHealth) {
        return maxHealth > 0f ? amount / maxHealth : 0f;
    }

    /**
     * Adds a frost hit's share, stacking on what stands with no diminishing,
     * capped at full; a hit that leaves the gauge full holds it the curve's
     * hold from now.
     *
     * @param share the share the hit adds
     * @param curve the hit's curve, which the gauge keeps
     * @param now   the game time
     * @return the gauge after the hit
     */
    public Frozen add(float share, FrostCurve curve, long now) {
        float after = Math.min(FULL, gauge + share);
        long hold = after >= FULL ? now + curve.holdTicks() : holdUntil;
        return new Frozen(after, hold, curve);
    }

    /**
     * Thaws the gauge one tick by the curve's rate, once any hold at full has run out.
     *
     * @param now the game time
     * @return the gauge after the tick: the same while held, NONE once thawed
     */
    public Frozen thawed(long now) {
        if (!started() || now < holdUntil) {
            return this;
        }
        float left = gauge - curve.thawPerTick();
        return left <= 0f ? NONE : new Frozen(left, holdUntil, curve);
    }

    /**
     * Answers whether the gauge is full: the freeze.
     *
     * @return true at full
     */
    public boolean full() {
        return gauge >= FULL;
    }

    /**
     * Answers whether any frost stands on the mob.
     *
     * @return true while the gauge stands above zero
     */
    public boolean started() {
        return gauge > 0f;
    }

    /**
     * The multiplier on a physical hit: whole at an empty gauge, rising in
     * proportion to the gauge to the curve's vulnerability above whole at full.
     *
     * @return the multiplier, 1 or more
     */
    public float physicalDamageMultiplier() {
        return 1f + curve.vulnerability() * gauge;
    }
}
