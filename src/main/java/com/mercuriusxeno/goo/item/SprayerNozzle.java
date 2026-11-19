package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.throwing.ConeShape;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

/**
 * The nozzle on the sprayer, picked on its wheel, in the wheel's order from fine and wide to tight and far: each
 * shapes the cone the residual's drips are thrown in, by how wide it opens across and up and down against the
 * residual's own cone, how many drips it throws and how far it carries them. The scales are tuning values.
 * operator ruling 2026-11-18: order Mist, Wide, Line, Spray (default), Thin, Jet; short names; Wide doubles the goo;
 * Thin goes farther and Jet farther still; Mist a wider cone at short range
 * decision spray-item-takes-different-nozzles-doing-different-things
 * decision choke-nozzle-narrows-the-cone
 * decision jet-nozzle-sprays-a-really-tight-stream
 * decision sprinkler-nozzle-sprays-evenly-across-a-really-wide-cone
 * decision fan-nozzle-flattens-and-widens-the-cone
 * decision mister-draws-mist-and-lays-one-layer-on-bare-spots
 */
public enum SprayerNozzle implements StringRepresentable {
    /** A fine mist over a wide round cone, short in reach, laying one layer on each bare spot it covers. */
    MIST("mist", 4.0, 4.0, 1.0, 1.0),
    /** A really wide round cone, the drips even over it, laying twice the goo. */
    WIDE("wide", 4.0, 4.0, 2.0, 1.0),
    /** Flattened and widened: very wide across the caster's left and right, thin up and down. */
    LINE("line", 6.0, 0.2, 1.0, 1.0),
    /** No nozzle: the residual's own cone, the default. */
    SPRAY("spray", 1.0, 1.0, 1.0, 1.0),
    /** Narrows the cone and carries farther. */
    THIN("thin", 0.5, 0.5, 1.0, 1.5),
    /** A really tight stream, tighter than Thin, carrying farther still. */
    JET("jet", 0.12, 0.12, 1.0, 2.2);

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
    private final double dripScale;
    private final double reachScale;

    SprayerNozzle(String name, double across, double upDown, double dripScale, double reachScale) {
        this.name = name;
        this.across = across;
        this.upDown = upDown;
        this.dripScale = dripScale;
        this.reachScale = reachScale;
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
     * @return true for Mist
     */
    public boolean mists() {
        return this == MIST;
    }

    /**
     * @return the multiple of the flow's drips a tick this nozzle throws
     */
    public double dripScale() {
        return dripScale;
    }

    /**
     * @return the multiple of the residual's exit speed this nozzle throws at, which carries the drips farther
     */
    public double reachScale() {
        return reachScale;
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
