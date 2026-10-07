package com.mercuriusxeno.goo.ability.petrify;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A mob's petrify gauge: it fills each tick a petrify stream holds the mob,
 * and at full the mob is a statue for good, encased in stone, its movement
 * neutered and its pose frozen
 * (decision petrify-stone-encasement-and-calcify-map).
 *
 * @param gauge  how far the gauge has filled, from zero to FULL
 * @param statue whether the mob has become a statue
 */
public record Petrification(float gauge, boolean statue) {

    /** The gauge a mob stands at before any petrify reaches it. */
    public static final Petrification NONE = new Petrification(0f, false);

    /** The gauge at which a mob becomes a statue. */
    public static final float FULL = 100f;

    private static final String FIELD_GAUGE = "gauge";
    private static final String FIELD_STATUE = "statue";

    /** Codec for the saved gauge. */
    public static final MapCodec<Petrification> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.FLOAT.fieldOf(FIELD_GAUGE).forGetter(Petrification::gauge),
            Codec.BOOL.fieldOf(FIELD_STATUE).forGetter(Petrification::statue)
    ).apply(inst, Petrification::new));

    /** Codec for the gauge synced to the clients drawing the mob. */
    public static final StreamCodec<ByteBuf, Petrification> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, Petrification::gauge,
            ByteBufCodecs.BOOL, Petrification::statue,
            Petrification::new);

    /**
     * Fills the gauge; reaching full makes the mob a statue, and a statue stays one.
     *
     * @param amount how much the gauge fills
     * @return the gauge after filling
     */
    public Petrification fill(float amount) {
        if (statue) {
            return this;
        }
        float filled = Math.min(FULL, gauge + amount);
        return new Petrification(filled, filled >= FULL);
    }

    /**
     * Answers whether the mob holds any petrification worth keeping.
     *
     * @return true once the gauge has begun to fill
     */
    public boolean started() {
        return gauge > 0f || statue;
    }
}
