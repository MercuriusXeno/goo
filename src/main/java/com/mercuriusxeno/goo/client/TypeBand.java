package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.ResourceKey;

/**
 * One goo type's layer of a mingled fluid surface (decision
 * noise-mingled-type-textures): layer 0, the first type in its order, draws whole, and
 * every later layer draws over the ones below at an opacity from its own
 * noise field whose mean is its conditional share, so each type covers its
 * volume ratio on average and blends with its neighbours at the seams.
 *
 * @param type  the goo type the layer shows
 * @param lo    the volume ratio of every earlier band together
 * @param hi    lo plus this type's volume ratio
 * @param layer the layer index, 0 for the first type in the band order
 */
public record TypeBand(ResourceKey<GooTypeDefinition> type, float lo, float hi, int layer) {

    /** Must match goo_fluid_surface.vsh: share units per whole share. */
    public static final int SHARE_UNITS = 16384;

    /**
     * Blocks each layer's geometry sits outward of the layer below, so
     * distance sorting draws the layers in order and depth never ties.
     */
    public static final float LAYER_LIFT = 1f / 512f;

    /** Bit offset of the noise seed in the packed band. */
    private static final int SEED_SHIFT = 16;

    /** The packed band of a lone whole surface: share 1, which draws whole whatever its seed. */
    public static final int BASE_LAYER_PACKED = SHARE_UNITS;

    /**
     * The layer's conditional share: this type's volume over the volume of
     * itself and every earlier band, 1 for layer 0. Over-blending the layers
     * in order leaves each type share_k times the product of (1 - share_j)
     * over the later layers, which telescopes to its volume ratio.
     *
     * @return the conditional share in [0, 1]
     */
    public float share() {
        return hi > 0f ? (hi - lo) / hi : 0f;
    }

    /**
     * @return the distance this layer's geometry sits outward of layer 0
     */
    public float lift() {
        return layer * LAYER_LIFT;
    }

    /**
     * The seed of this type's own noise field, the same in every mix and at
     * every layer, so no layer renumbering reshuffles a pattern (decision
     * mingle-noise-keyed-by-type).
     *
     * @return the type's position in {@link GooTypes#order()}, or 0 for a type the capture does not hold
     */
    public int seed() {
        return Math.max(0, GooTypes.indexOf(type));
    }

    /**
     * Packs the share and the seed into the lightmap coordinates the
     * surface shader reads as UV2, share in the low short and seed in the
     * high short; the surface is emissive, so its lightmap coordinates carry
     * nothing else.
     *
     * @return the band as packed lightmap coordinates
     */
    public int packed() {
        return Math.clamp(Math.round(share() * SHARE_UNITS), 0, SHARE_UNITS) | seed() << SEED_SHIFT;
    }
}
