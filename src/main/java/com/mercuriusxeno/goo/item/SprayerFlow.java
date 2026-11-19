package com.mercuriusxeno.goo.item;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

/**
 * How much the sprayer sprays a tick, picked on its wheel in steps from low to high: each step throws its rate
 * times the residual's own drips a tick, a rate under one throwing a drip on some ticks alone. The rates are tuning
 * values.
 * decision spray-item-sprays-more-or-less-per-tick
 */
public enum SprayerFlow implements StringRepresentable {
    LOW("low", 0.5),
    MEDIUM("medium", 1.0),
    HIGH("high", 2.0);

    /** Persistent codec, by name. */
    public static final Codec<SprayerFlow> CODEC = StringRepresentable.fromEnum(SprayerFlow::values);
    /** Network codec, by ordinal. */
    public static final StreamCodec<ByteBuf, SprayerFlow> STREAM_CODEC =
            ByteBufCodecs.idMapper(ordinal -> values()[ordinal], SprayerFlow::ordinal);

    /** The lang key prefix of a step's name on the wheel. */
    private static final String LANG_PREFIX = "item.goo.sprayer.flow.";

    private final String name;
    private final double rate;

    SprayerFlow(String name, double rate) {
        this.name = name;
        this.rate = rate;
    }

    @Override
    public @NonNull String getSerializedName() {
        return name;
    }

    /**
     * @return the lang key of the step's name on the wheel
     */
    public String langKey() {
        return LANG_PREFIX + name;
    }

    /**
     * @return the multiple of the residual's drips a tick this step throws
     */
    public double rate() {
        return rate;
    }

    /**
     * @return the step throwing the fewest drips
     */
    public static SprayerFlow lowest() {
        return LOW;
    }

    /**
     * @return the step throwing the most drips
     */
    public static SprayerFlow highest() {
        return HIGH;
    }
}
