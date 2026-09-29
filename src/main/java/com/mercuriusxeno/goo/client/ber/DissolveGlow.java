package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.client.TypeBand;
import com.mercuriusxeno.goo.client.TypeBands;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * How far a melting item has dissolved and the goo type layers its edge glows in,
 * one per type the item yields, built as the mingled surface builds its bands so the
 * glow picks one type per fragment and never an average (decisions dissolve-shader-on-item,
 * glow-color-from-mingling).
 *
 * <p>Each layer draws the item once. The overlay coordinates carry the fraction in the low
 * half and the layer's color as RGB565 in the high half; the lightmap coordinates keep the
 * light level in the high nibble of each half's low byte, carry the melt's seed in the two
 * low nibbles the lightmap lookup drops, and the layer's share and index in the high bytes
 * (decision diagnose-then-fix-dissolve-repeat).
 *
 * @param fraction the share of the item dissolved, from 0 whole to 1 gone
 * @param seed     the melt's seed, from 0 to {@link #SEED_UNITS} less one, which offsets the
 *                 dissolve field so two melts erode in different orders
 * @param layers   the glow layers, largest type first; the first draws the item's body
 */
public record DissolveGlow(float fraction, int seed, List<Layer> layers) {

    /** Overlay units per whole fraction; must match FRACTION_UNITS in crucible_dissolve.vsh. */
    public static final int FRACTION_UNITS = 4096;
    /** Share units per whole share; must match SHARE_UNITS in crucible_dissolve.vsh. */
    public static final int SHARE_UNITS = 127;
    /** The seed's bits per lightmap half; must match SEED_NIBBLE_BITS in crucible_dissolve.vsh. */
    public static final int SEED_NIBBLE_BITS = 4;
    /** The low nibble of a lightmap half; must match SEED_NIBBLE_MASK in crucible_dissolve.vsh. */
    public static final int SEED_NIBBLE_MASK = 0xF;
    /** The seeds a melt draws among, the two nibbles' worth. */
    public static final int SEED_UNITS = 1 << (2 * SEED_NIBBLE_BITS);

    /** The light level's bits in a lightmap half's low byte, the nibble above the seed's. */
    private static final int LIGHT_LEVEL_MASK = 0xF0;
    private static final int BYTE = 0xFF;
    private static final int HALF_MASK = 0xFFFF;
    private static final int RED_SHIFT = 16;
    private static final int GREEN_SHIFT = 8;
    private static final int FIVE_BIT_DROP = 3;
    private static final int SIX_BIT_DROP = 2;
    private static final int RED_565_SHIFT = 11;
    private static final int GREEN_565_SHIFT = 5;
    private static final int HIGH_HALF_SHIFT = 16;
    private static final int HIGH_BYTE_SHIFT = 8;
    /** The layers a glow keeps, the most the lightmap's high byte indexes with room to spare. */
    private static final int MAX_LAYERS = 127;

    /**
     * One goo type's glow layer.
     *
     * @param glowRgb the type's color as 0xRRGGBB; any alpha byte is ignored
     * @param share   the layer's conditional share, as {@link TypeBand#share()}
     * @param layer   the layer index, 0 for the largest type
     */
    public record Layer(int glowRgb, float share, int layer) {
    }

    /**
     * Builds the glow of an item from the goo it yields, one layer per type band in volume
     * ratio, largest first.
     *
     * @param fraction the share of the item dissolved
     * @param value    the goo the item yields
     * @param colorOf  the color each goo type glows in
     * @return the glow
     */
    public static DissolveGlow of(float fraction, GooValue value,
                                  ToIntFunction<ResourceKey<GooTypeDefinition>> colorOf) {
        List<Layer> layers = new ArrayList<>();
        for (TypeBand band : TypeBands.largestFirst(value.toGooContents())) {
            if (band.layer() < MAX_LAYERS) {
                layers.add(new Layer(colorOf.applyAsInt(band.type()), band.share(), band.layer()));
            }
        }
        return new DissolveGlow(fraction, 0, layers);
    }

    /**
     * Returns this glow for the melt the seed names.
     *
     * @param meltSeed the melt's seed, from 0 to {@link #SEED_UNITS} less one
     * @return the glow carrying the seed
     */
    public DissolveGlow withSeed(int meltSeed) {
        return new DissolveGlow(fraction, meltSeed, layers);
    }

    /**
     * Returns a glow of one layer in one color, for an item whose goo the client does not know.
     *
     * @param fraction the share of the item dissolved
     * @param glowRgb  the color it glows in
     * @return the glow
     */
    public static DissolveGlow single(float fraction, int glowRgb) {
        return new DissolveGlow(fraction, 0, List.of(new Layer(glowRgb, 1f, 0)));
    }

    /**
     * Returns the fraction in overlay units, clamped to a whole item.
     *
     * @return the fraction units, from 0 to {@link #FRACTION_UNITS}
     */
    public int fractionUnits() {
        return Math.round(Math.clamp(fraction, 0f, 1f) * FRACTION_UNITS);
    }

    /**
     * Returns a color packed as RGB565.
     *
     * @param rgb the color as 0xRRGGBB
     * @return the 16-bit color
     */
    public static int rgb565(int rgb) {
        int red = (rgb >> RED_SHIFT) & BYTE;
        int green = (rgb >> GREEN_SHIFT) & BYTE;
        int blue = rgb & BYTE;
        return (red >> FIVE_BIT_DROP) << RED_565_SHIFT
                | (green >> SIX_BIT_DROP) << GREEN_565_SHIFT
                | blue >> FIVE_BIT_DROP;
    }

    /**
     * Returns the overlay coordinates of one layer: fraction units low, the layer's RGB565 high.
     *
     * @param layer the glow layer
     * @return the overlay coordinates
     */
    public int overlayCoords(Layer layer) {
        return fractionUnits() | rgb565(layer.glowRgb()) << HIGH_HALF_SHIFT;
    }

    /**
     * Returns the lightmap coordinates of one layer: each half's low byte keeps the light
     * level over a nibble of the seed, the block half's high byte the share and the sky
     * half's high byte the layer index.
     *
     * @param light the packed light coordinates the item is lit at
     * @param layer the glow layer
     * @return the lightmap coordinates
     */
    public int lightCoords(int light, Layer layer) {
        int shareUnits = Math.clamp(Math.round(layer.share() * SHARE_UNITS), 0, SHARE_UNITS);
        int block = lightHalf(light, seed, shareUnits);
        int sky = lightHalf(light >> HIGH_HALF_SHIFT, seed >> SEED_NIBBLE_BITS, layer.layer());
        return (block & HALF_MASK) | sky << HIGH_HALF_SHIFT;
    }

    /**
     * Returns one lightmap half: the light level, a nibble of the seed below it and a byte above.
     *
     * @param light    the light half, its level in the low byte's high nibble
     * @param seedBits the seed shifted so its nibble for this half sits lowest
     * @param highByte the value the half's high byte carries
     * @return the lightmap half
     */
    private static int lightHalf(int light, int seedBits, int highByte) {
        int lowByte = (light & LIGHT_LEVEL_MASK) | (seedBits & SEED_NIBBLE_MASK);
        return lowByte | highByte << HIGH_BYTE_SHIFT;
    }
}
