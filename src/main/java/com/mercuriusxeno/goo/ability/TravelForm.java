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
    BLOB(0f, 0f, Fins.NONE, Plate.NONE),
    /**
     * A long needle-pointed spear with flat swept fins at its tail.
     * decision dart-flutes-draw-as-fins
     */
    DART(1.25f, 0.025f, new Fins(3, 0.225f, 0.08f, 0.075f, 0.09f), Plate.NONE),
    /**
     * A heater shield of goo standing square to the look, square-shouldered
     * and narrowing to its foot, the goo Durable's Shield forms in the hand.
     * decision durable-goo-forms-a-shield-at-will-and-shield-bashes
     */
    SHIELD(0f, 0f, Fins.NONE, new Plate(0.8f, 1.0f, 0.08f));

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
    private final Plate plate;

    TravelForm(float frontLength, float frontRadius, Fins fins, Plate plate) {
        this.frontLength = frontLength;
        this.frontRadius = frontRadius;
        this.fins = fins;
        this.plate = plate;
    }

    /**
     * A flat plate standing square to the direction a form points along, all
     * in blocks at the form's whole size.
     *
     * @param width  how wide the plate stands across its shoulders
     * @param height how tall it stands from its top edge to its foot
     * @param depth  how thick it is along the direction
     */
    public record Plate(float width, float height, float depth) implements Serializable {
        /** No plate. */
        public static final Plate NONE = new Plate(0f, 0f, 0f);

        /**
         * Whether the form stands as a plate.
         *
         * @return true for a plate of any size
         */
        public boolean stands() {
            return width > 0f && height > 0f;
        }
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

    /** @return the plate the form stands as, none for a pointed form */
    public Plate plate() {
        return plate;
    }
}
