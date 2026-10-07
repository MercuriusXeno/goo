package com.mercuriusxeno.goo.ability.petrify;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A mob's petrify gauge: it fills each tick Petrify's fog holds the mob, the
 * mob slowing and stone patches spreading over it as it fills, and once the
 * fog has left it a while it drains back slowly; at full the mob becomes a
 * statue block for good (decision petrify-stone-encasement-and-calcify-map).
 *
 * @param gauge    how far the gauge has filled, from zero to FULL
 * @param lastFill the game time the fog last filled it
 */
public record Petrification(float gauge, long lastFill) {

    /** The gauge a mob stands at before any petrify reaches it. */
    public static final Petrification NONE = new Petrification(0f, 0L);

    /** The gauge at which a mob becomes a statue. */
    public static final float FULL = 100f;

    /** Ticks after the fog leaves before the gauge starts draining. */
    static final long DRAIN_DELAY_TICKS = 20L;

    /** How much the gauge drains each tick once draining: a full gauge drains in ten seconds. */
    static final float DRAIN_PER_TICK = FULL / 200f;

    private static final String FIELD_GAUGE = "gauge";
    private static final String FIELD_LAST_FILL = "last_fill";

    /** Codec for the saved gauge. */
    public static final MapCodec<Petrification> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.FLOAT.fieldOf(FIELD_GAUGE).forGetter(Petrification::gauge),
            Codec.LONG.fieldOf(FIELD_LAST_FILL).forGetter(Petrification::lastFill)
    ).apply(inst, Petrification::new));

    /** Codec for the gauge synced to the clients drawing the mob. */
    public static final StreamCodec<ByteBuf, Petrification> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, Petrification::gauge,
            ByteBufCodecs.VAR_LONG, Petrification::lastFill,
            Petrification::new);

    /**
     * Fills the gauge, capped at full.
     *
     * @param amount how much the gauge fills
     * @param now    the game time
     * @return the gauge after filling
     */
    public Petrification fill(float amount, long now) {
        return new Petrification(Math.min(FULL, gauge + amount), now);
    }

    /**
     * Drains the gauge one tick, once the fog has left it long enough.
     *
     * @param now the game time
     * @return the gauge after the tick: the same while the fog lingers, NONE once empty
     */
    public Petrification drained(long now) {
        if (now - lastFill <= DRAIN_DELAY_TICKS) {
            return this;
        }
        float left = gauge - DRAIN_PER_TICK;
        return left <= 0f ? NONE : new Petrification(left, lastFill);
    }

    /**
     * Answers whether the gauge has filled.
     *
     * @return true at full, the tick the mob becomes a statue
     */
    public boolean full() {
        return gauge >= FULL;
    }

    /**
     * The share of the way to a statue, which slows the mob and spreads its stone.
     *
     * @return the gauge as a share of full, 0 to 1
     */
    public float share() {
        return gauge / FULL;
    }

    /**
     * Answers whether the mob holds any petrification worth keeping.
     *
     * @return true while the gauge stands above zero
     */
    public boolean started() {
        return gauge > 0f;
    }
}
