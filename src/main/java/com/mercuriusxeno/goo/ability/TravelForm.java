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
    BLOB(0f, 0f, Fins.NONE, Barbs.NONE),
    /**
     * A long needle-pointed spear with flat swept fins at its tail.
     * decision dart-flutes-draw-as-fins
     */
    DART(1.25f, 0.025f, new Fins(3, 0.225f, 0.08f, 0.075f, 0.09f), Barbs.NONE),
    /**
     * Spear's javelin: a longer, thinner needle than Dart's with no fins or fletching, barbed by two rings of
     * three backward hooks along its point.
     * decision spear-is-a-barbed-javelin-in-the-dart-family
     */
    SPEAR(1.4f, 0.02f, Fins.NONE, new Barbs(3, 2, 1.0f, 0.3f, 0.1f, 0.05f, 0.06f));

    private static final String WHAT = "travel form";
    /** The fins stand out on both sides of the shaft across any width taken through it. */
    private static final int SIDES_OF_THE_SHAFT = 2;

    /** Codec reading the lower-case name. */
    public static final Codec<TravelForm> CODEC = LowerCaseEnumCodec.of(TravelForm.class, WHAT);

    /** Stream codec carrying the form on the ability sync and the flight broadcast. */
    public static final StreamCodec<ByteBuf, TravelForm> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(ordinal -> values()[ordinal], TravelForm::ordinal);

    private final float frontLength;
    private final float frontRadius;
    private final Fins fins;
    private final Barbs barbs;

    TravelForm(float frontLength, float frontRadius, Fins fins, Barbs barbs) {
        this.frontLength = frontLength;
        this.frontRadius = frontRadius;
        this.fins = fins;
        this.barbs = barbs;
    }

    /**
     * The hooks along a form's point, rings of them evenly spaced around the
     * body, each a flat triangle whose root lies along the body and whose tip
     * points back and out, all in blocks at the form's whole size.
     * decision spear-is-a-barbed-javelin-in-the-dart-family
     *
     * @param count       how many barbs stand evenly spaced around each ring
     * @param rings       how many rings stand along the point
     * @param firstAt     how far ahead of the cone's base the first ring's roots begin
     * @param ringSpacing how far behind each ring the next begins
     * @param rootChord   the length of each barb's root along the body
     * @param span        how far each barb's tip stands out from the body
     * @param back        how far behind its root's rear end each barb's tip reaches
     */
    public record Barbs(int count, int rings, float firstAt, float ringSpacing, float rootChord, float span,
                        float back) implements Serializable {
        /** No barbs. */
        public static final Barbs NONE = new Barbs(0, 0, 0f, 0f, 0f, 0f, 0f);
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

    /**
     * How wide the form stands across its fins at its whole size: each fin
     * stands its span out from the shaft, so twice the span; none for a form
     * with no fins. Dart's hovering javelins space by it.
     * decision dart-javelins-hover-over-the-shrinking-blob-cube
     *
     * @return the width in blocks
     */
    public float widthAcrossFins() {
        return SIDES_OF_THE_SHAFT * fins.span();
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

    /** @return the barbs along the form's point */
    public Barbs barbs() {
        return barbs;
    }
}
