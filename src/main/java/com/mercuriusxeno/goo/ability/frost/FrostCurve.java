package com.mercuriusxeno.goo.ability.frost;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * How a frozen gauge behaves once a frost hit fills it, as the hit's JSON
 * names it: how long a full gauge holds, how fast it thaws after, and how
 * much more a physical hit deals at full.
 * frozen-gauge-per-mob-encases-when-full
 *
 * @param holdTicks     ticks a full gauge holds before the thaw resumes
 * @param thawPerTick   the share of a full gauge that thaws each tick
 * @param vulnerability the extra share of a physical hit taken at a full gauge
 */
public record FrostCurve(int holdTicks, float thawPerTick, float vulnerability) {

    /** The curve of a mob no frost has reached. */
    public static final FrostCurve NONE = new FrostCurve(0, 0f, 0f);

    private static final String FIELD_HOLD = "hold";
    private static final String FIELD_THAW = "thaw";
    private static final String FIELD_VULNERABILITY = "vulnerability";

    /** Codec for the curve's fields, which the freeze step reads inline and the gauge saves. */
    public static final MapCodec<FrostCurve> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.INT.fieldOf(FIELD_HOLD).forGetter(FrostCurve::holdTicks),
            Codec.FLOAT.fieldOf(FIELD_THAW).forGetter(FrostCurve::thawPerTick),
            Codec.FLOAT.fieldOf(FIELD_VULNERABILITY).forGetter(FrostCurve::vulnerability)
    ).apply(inst, FrostCurve::new));

    /** Codec for the curve synced with the gauge. */
    public static final StreamCodec<ByteBuf, FrostCurve> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, FrostCurve::holdTicks,
            ByteBufCodecs.FLOAT, FrostCurve::thawPerTick,
            ByteBufCodecs.FLOAT, FrostCurve::vulnerability,
            FrostCurve::new);
}
