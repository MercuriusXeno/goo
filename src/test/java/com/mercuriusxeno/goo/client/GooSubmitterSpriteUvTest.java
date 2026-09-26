package com.mercuriusxeno.goo.client;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers GooSubmitter's sprite-to-UvRect conversion, the one place a renderer
 * reads a sprite's atlas bounds (decision submitter-owns-every-render-choice).
 */
class GooSubmitterSpriteUvTest {

    private static final float EPSILON = 1e-6f;

    private static TextureAtlasSprite spriteAt(float u0, float v0, float u1, float v1) {
        TextureAtlasSprite sprite = mock(TextureAtlasSprite.class);
        when(sprite.getU0()).thenReturn(u0);
        when(sprite.getV0()).thenReturn(v0);
        when(sprite.getU1()).thenReturn(u1);
        when(sprite.getV1()).thenReturn(v1);
        return sprite;
    }

    @Test
    void wholeSpriteCoversItsAtlasRegion() {
        GooRenderUtil.UvRect uv = GooSubmitter.spriteUv(spriteAt(0.25f, 0.5f, 0.375f, 0.625f));
        assertEquals(new GooRenderUtil.UvRect(0.25f, 0.5f, 0.375f, 0.625f), uv);
    }

    @Test
    void subRectScalesLocalExtentsIntoTheAtlasRegion() {
        GooRenderUtil.UvRect uv = GooSubmitter.spriteSubRect(
            spriteAt(0.25f, 0.5f, 0.375f, 0.625f), 0f, 0.5f, 0.5f, 1f);
        assertEquals(0.25f, uv.u0(), EPSILON);
        assertEquals(0.5625f, uv.v0(), EPSILON);
        assertEquals(0.3125f, uv.u1(), EPSILON);
        assertEquals(0.625f, uv.v1(), EPSILON);
    }
}
