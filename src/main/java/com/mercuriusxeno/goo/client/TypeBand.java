package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.ResourceKey;

/**
 * One goo type's layer of a mingled fluid surface (decisions
 * noise-mingled-type-textures and one-pass-blends-every-type): the surface
 * draws once, and its shader blends layer 0, the first type in its order,
 * whole, then every later layer over the ones before at an opacity from its
 * own noise field whose mean is its conditional share, so each type covers
 * its volume ratio on average and blends with its neighbours at the seams.
 *
 * @param type  the goo type the layer shows
 * @param lo    the volume ratio of every earlier band together
 * @param hi    lo plus this type's volume ratio
 * @param layer the layer index, 0 for the first type in the band order
 */
public record TypeBand(ResourceKey<GooTypeDefinition> type, float lo, float hi, int layer) {

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
     * The seed of this type's own noise field, the same in every mix and at
     * every layer, so no layer renumbering reshuffles a pattern (decision
     * mingle-noise-keyed-by-type).
     *
     * @return the type's position in {@link GooTypes#order()}, or 0 for a type the capture does not hold
     */
    public int seed() {
        return Math.max(0, GooTypes.indexOf(type));
    }
}
