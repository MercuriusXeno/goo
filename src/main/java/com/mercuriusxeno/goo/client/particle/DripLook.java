package com.mercuriusxeno.goo.client.particle;

import com.mercuriusxeno.goo.client.GooRenderUtil;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * What a drip particle draws: the sprite, the part of it the quad spans,
 * and the vertex color over it.
 *
 * @param sprite the atlas sprite
 * @param uv     the atlas UVs the quad spans
 * @param rgb    the vertex color
 */
record DripLook(TextureAtlasSprite sprite, GooRenderUtil.UvRect uv, int rgb) {
}
