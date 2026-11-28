package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The giga drill Bore swells into while its drill passes through Mortar,
 * set in rock_bore.json beside Bore's own drill: a wider and longer cone,
 * held at Bore's angle with the same share of it past the crosshair's plane,
 * so its cross section is wider by the same measure; it is paid in Mortar,
 * a layer drained from the network each cadence.
 * boring-through-rock-goo-gives-a-giga-drill-that-drains-it
 *
 * @param width         the giga drill's width at the blob, in blocks
 * @param length        the giga drill's length from the blob to its point, in blocks
 * @param ticksPerLayer the ticks between layers drained from the Mortar network
 */
public record GigaDrill(double width, double length, int ticksPerLayer) {

    /** rock_bore.json's giga drill, which a bore naming none takes. */
    public static final GigaDrill SHIPPED = new GigaDrill(4, 6, 2);

    /**
     * Codec for the giga drill's three numbers, read flat among the bore step's own fields.
     */
    public static final MapCodec<GigaDrill> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.doubleRange(0, Double.MAX_VALUE).optionalFieldOf("giga_drill_width", SHIPPED.width())
                    .forGetter(GigaDrill::width),
            Codec.doubleRange(0, Double.MAX_VALUE).optionalFieldOf("giga_drill_length", SHIPPED.length())
                    .forGetter(GigaDrill::length),
            Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("giga_ticks_per_layer", SHIPPED.ticksPerLayer())
                    .forGetter(GigaDrill::ticksPerLayer)
    ).apply(inst, GigaDrill::new));

    /**
     * Bore's own drill swollen to the giga drill: this width and length, Bore's
     * angle, and its past length grown with the length, so the cone meets the
     * crosshair's plane at the same share of its length.
     *
     * @param own Bore's own drill
     * @return the giga drill's cone
     */
    public BoreDrillShape swell(BoreDrillShape own) {
        return new BoreDrillShape(width, length, own.angle(), own.pastLength() * length / own.length());
    }
}
