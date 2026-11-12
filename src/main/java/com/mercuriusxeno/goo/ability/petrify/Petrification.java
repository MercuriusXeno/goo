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
 * statue block (decision petrify-stone-encasement-and-calcify-map), which
 * stands until mined or until Heal's stream wakes it
 * (decision heal-wakes-a-statue-at-a-nearly-full-gauge).
 *
 * @param gauge    how far the gauge has filled, from zero to FULL
 * @param lastFill the game time the fog last filled it
 */
public record Petrification(float gauge, long lastFill) {

    /** The gauge a mob stands at before any petrify reaches it. */
    public static final Petrification NONE = new Petrification(0f, 0L);

    /** The gauge at which a mob becomes a statue. */
    public static final float FULL = 100f;

    /**
     * The gauge a statue wakes at under Heal: just under full, so the mob
     * stands slowed and stone-covered until a drain takes the stone off.
     * decision heal-wakes-a-statue-at-a-nearly-full-gauge
     */
    static final float WAKING_GAUGE = FULL - 1f;

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
     * The gauge of a mob a statue woke into this tick, held there for the
     * fog's drain delay as though the fog had just filled it.
     *
     * @param now the game time
     * @return the waking gauge
     */
    public static Petrification waking(long now) {
        return new Petrification(WAKING_GAUGE, now);
    }

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
     * Drains the gauge by an amount at once, as Heal's stream does each held
     * tick, keeping the time the fog last filled it so the fog's own drain
     * carries on from where the stream left the gauge.
     * decision heal-stream-drains-the-petrify-gauge
     *
     * @param amount how much the gauge drains
     * @return the gauge after draining, NONE once empty
     */
    public Petrification drainedBy(float amount) {
        float left = gauge - amount;
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
