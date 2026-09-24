package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;

/**
 * One goo type's share of a mingled fluid surface (decision
 * noise-mingled-type-textures): the surface shader keeps a fragment of this
 * type's texture where its noise falls in [lo, hi), so the band's width is
 * the share of the surface the type covers.
 *
 * @param type the goo type the band shows
 * @param lo   the band's inclusive lower edge in cumulative volume ratio
 * @param hi   the band's exclusive upper edge in cumulative volume ratio
 */
public record TypeBand(ResourceKey<GooTypeDefinition> type, float lo, float hi) {

    /** Must match goo_fluid_surface.vsh: band units per whole volume ratio. */
    public static final int BAND_UNITS = 16384;

    /** Bit offset of the upper edge in the packed band. */
    private static final int HI_SHIFT = 16;

    /** The packed band spanning the whole range, which a single-type surface draws. */
    public static final int WHOLE_RANGE_PACKED = BAND_UNITS << HI_SHIFT;

    /**
     * Packs the band into the lightmap coordinates the surface shader reads
     * as UV2, lower edge in the low short and upper edge in the high short;
     * the surface is emissive, so its lightmap coordinates carry nothing else.
     *
     * @return the band as packed lightmap coordinates
     */
    public int packed() {
        return toUnits(lo) | toUnits(hi) << HI_SHIFT;
    }

    /**
     * Converts a volume ratio to band units.
     *
     * @param ratio the cumulative volume ratio in [0, 1]
     * @return the ratio in 1/{@value #BAND_UNITS}
     */
    private static int toUnits(float ratio) {
        return Math.clamp(Math.round(ratio * BAND_UNITS), 0, BAND_UNITS);
    }
}
