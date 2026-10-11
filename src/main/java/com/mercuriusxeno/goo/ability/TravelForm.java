package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.ability.program.LowerCaseEnumCodec;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The form a thrown goo takes in flight: it stays a blob, or it morphs
 * into a pointed shape by the share of the flight its delivery's
 * transform_at names, the metal javelin's dart.
 * decision traveling-form-transforms-in-flight
 *
 * @see Delivery#transformAt()
 */
public enum TravelForm {
    /** The goo stays a blob the whole flight. */
    BLOB(0f, 0f, 0f, 0f),
    /** A long needle-pointed spear with a stubby butt. */
    DART(2.5f, 0.05f, 0.5f, 0.09f);

    private static final String WHAT = "travel form";

    /** Codec reading the lower-case name. */
    public static final Codec<TravelForm> CODEC = LowerCaseEnumCodec.of(TravelForm.class, WHAT);

    /** Stream codec carrying the form on the ability sync and the flight broadcast. */
    public static final StreamCodec<ByteBuf, TravelForm> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(ordinal -> values()[ordinal], TravelForm::ordinal);

    private final float frontLength;
    private final float frontRadius;
    private final float rearLength;
    private final float rearRadius;

    TravelForm(float frontLength, float frontRadius, float rearLength, float rearRadius) {
        this.frontLength = frontLength;
        this.frontRadius = frontRadius;
        this.rearLength = rearLength;
        this.rearRadius = rearRadius;
    }

    /**
     * Whether the goo morphs into a shape in flight.
     *
     * @return true for every form but the blob
     */
    public boolean morphs() {
        return this != BLOB;
    }

    /** @return the forward cone's length in blocks, whole */
    public float frontLength() {
        return frontLength;
    }

    /** @return the forward cone's base radius in blocks, whole */
    public float frontRadius() {
        return frontRadius;
    }

    /** @return the rear cone's length in blocks, whole */
    public float rearLength() {
        return rearLength;
    }

    /** @return the rear cone's base radius in blocks, whole */
    public float rearRadius() {
        return rearRadius;
    }
}
