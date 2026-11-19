package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.throwing.ConeShape;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

/**
 * The nozzle on the sprayer, picked on its wheel: each shapes the cone the residual's drips are thrown in, by how
 * wide it opens across and up and down against the residual's own cone. The scales are tuning values.
 * decision spray-item-takes-different-nozzles-doing-different-things
 * decision choke-nozzle-narrows-the-cone
 * decision jet-nozzle-sprays-a-really-tight-stream
 * decision sprinkler-nozzle-sprays-evenly-across-a-really-wide-cone
 * decision fan-nozzle-flattens-and-widens-the-cone
 * decision mister-draws-mist-and-lays-one-layer-on-bare-spots
 */
public enum SprayerNozzle implements StringRepresentable {
    /** No nozzle: the residual's own cone. */
    OPEN("open", 1.0, 1.0),
    /** A fine mist over the residual's own cone, laying one layer on each bare spot it covers. */
    ATOMIZER("atomizer", 1.0, 1.0),
    /** Narrows the cone. */
    CHOKE("choke", 0.5, 0.5),
    /** A really tight stream, tighter than the choke. */
    JET("jet", 0.12, 0.12),
    /** A really wide cone, the drips even over it, as every cone's drips are spread by area. */
    SPRINKLER("sprinkler", 3.0, 3.0),
    /** Flattened and widened: wide across the caster's left and right, thin up and down. */
    FAN("fan", 2.5, 0.3);

    /** Persistent codec, by name. */
    public static final Codec<SprayerNozzle> CODEC = StringRepresentable.fromEnum(SprayerNozzle::values);
    /** Network codec, by ordinal. */
    public static final StreamCodec<ByteBuf, SprayerNozzle> STREAM_CODEC =
            ByteBufCodecs.idMapper(ordinal -> values()[ordinal], SprayerNozzle::ordinal);

    /** The lang key prefix of a nozzle's name on the wheel. */
    private static final String LANG_PREFIX = "item.goo.sprayer.nozzle.";

    private final String name;
    private final double across;
    private final double upDown;

    SprayerNozzle(String name, double across, double upDown) {
        this.name = name;
        this.across = across;
        this.upDown = upDown;
    }

    @Override
    public @NonNull String getSerializedName() {
        return name;
    }

    /**
     * @return the lang key of the nozzle's name on the wheel
     */
    public String langKey() {
        return LANG_PREFIX + name;
    }

    /**
     * Whether the nozzle mists, laying a layer on each bare spot its cone covers, in place of throwing drips.
     * decision mister-nozzle-applies-one-layer-at-most
     *
     * @return true for the atomizer
     */
    public boolean mists() {
        return this == ATOMIZER;
    }

    /**
     * The cone this nozzle throws a residual's drips in.
     *
     * @param coneDegrees the residual's own cone, apex to rim, in degrees
     * @return the cone's widths across and up and down
     */
    public ConeShape shape(double coneDegrees) {
        return new ConeShape(coneDegrees * across, coneDegrees * upDown);
    }
}
