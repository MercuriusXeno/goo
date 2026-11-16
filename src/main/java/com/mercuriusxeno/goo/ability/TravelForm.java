package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.ability.program.LowerCaseEnumCodec;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import java.io.Serializable;

/**
 * The form a thrown goo takes in flight: it stays a blob, or it morphs
 * into a pointed shape by the share of the flight its delivery's
 * transform_at names, the metal Dart's dart.
 * decision traveling-form-transforms-in-flight
 *
 * @see Delivery#transformAt()
 */
public enum TravelForm {
    /** The goo stays a blob the whole flight. */
    BLOB(0f, 0f, Fins.NONE),
    /**
     * A long needle-pointed spear with flat swept fins at its tail.
     * decision dart-flutes-draw-as-fins
     */
    DART(2.5f, 0.05f, new Fins(3, 0.45f, 0.16f, 0.15f, 0.18f));

    private static final String WHAT = "travel form";

    /** Codec reading the lower-case name. */
    public static final Codec<TravelForm> CODEC = LowerCaseEnumCodec.of(TravelForm.class, WHAT);

    /** Stream codec carrying the form on the ability sync and the flight broadcast. */
    public static final StreamCodec<ByteBuf, TravelForm> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(ordinal -> values()[ordinal], TravelForm::ordinal);

    private final float frontLength;
    private final float frontRadius;
    private final Fins fins;

    TravelForm(float frontLength, float frontRadius, Fins fins) {
        this.frontLength = frontLength;
        this.frontRadius = frontRadius;
        this.fins = fins;
    }

    /**
     * The flat vanes at a form's tail, each lying along the shaft and
     * standing out from it, all in blocks at the form's whole size.
     *
     * @param count     how many fins stand evenly spaced around the shaft
     * @param rootChord the length of the edge joining the fin to the shaft,
     *                  running forward from the form's middle
     * @param span      how far the fin stands out from the shaft
     * @param sweep     how far behind the form's middle the outer edge ends
     * @param tipChord  the length of the outer edge
     */
    public record Fins(int count, float rootChord, float span, float sweep, float tipChord) implements Serializable {
        /** No fins. */
        public static final Fins NONE = new Fins(0, 0f, 0f, 0f, 0f);
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

    /** @return the fins at the form's tail */
    public Fins fins() {
        return fins;
    }
}
