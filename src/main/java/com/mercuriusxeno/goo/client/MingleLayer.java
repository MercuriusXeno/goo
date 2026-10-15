package com.mercuriusxeno.goo.client;

/**
 * One goo type's entry in the table a mingled surface blends from (decision
 * one-pass-blends-every-type): where its sprite sits on the block atlas, the
 * tint it draws under, its conditional share and its noise seed.
 *
 * @param sprite the atlas rect a sprite-relative UV maps into
 * @param tint   the ARGB tint the sprite draws under
 * @param share  the layer's conditional share, 0 to 1
 * @param seed   the type's noise seed
 */
public record MingleLayer(GooRenderUtil.UvRect sprite, int tint, float share, int seed) {

    /** The rect that maps a UV to itself, for an emitter that already writes atlas UVs. */
    public static final GooRenderUtil.UvRect ATLAS_UV = new GooRenderUtil.UvRect(0f, 0f, 1f, 1f);
}
