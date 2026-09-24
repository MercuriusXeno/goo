package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mercuriusxeno.goo.client.TypeBand;
import com.mercuriusxeno.goo.client.TypeBands;
import com.mercuriusxeno.goo.item.GooContents;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests that a vat holding two goo types submits one surface per type, each
 * on its own type's sprite, with every vertex carrying that type's band.
 */
class VatMingledSurfaceTest {

    /** Each type's sprite sits in its own quarter of the atlas along U. */
    private static final Map<ResourceKey<GooTypeDefinition>, Float> SPRITE_U0 =
        Map.of(GooTypes.FROST, 0f, GooTypes.BLAZE, 0.5f);
    private static final float SPRITE_WIDTH = 0.25f;

    private record Surface(TypeBand band, TextureAtlasSprite sprite, List<RecordingVertexConsumer.Vertex> vertices) {
    }

    private static TextureAtlasSprite spriteOf(ResourceKey<GooTypeDefinition> type) {
        float u0 = SPRITE_U0.get(type);
        TextureAtlasSprite sprite = mock(TextureAtlasSprite.class);
        when(sprite.getU0()).thenReturn(u0);
        when(sprite.getU1()).thenReturn(u0 + SPRITE_WIDTH);
        when(sprite.getV0()).thenReturn(0f);
        when(sprite.getV1()).thenReturn(1f);
        return sprite;
    }

    private static float decode(int units) {
        return (float) units / TypeBand.BAND_UNITS;
    }

    @Test
    void twoTypesSubmitTwoSurfacesCarryingComplementaryBandsOnTheirOwnSprites() {
        // BlockEntityRenderState's constructor bootstraps Blocks, so the state is built without it.
        VatRenderState state = mock(VatRenderState.class);
        state.stackSize = 1;
        state.fillFraction = 0.5f;
        state.typeBands = TypeBands.over(new GooContents(Map.of(GooTypes.BLAZE, 250, GooTypes.FROST, 750)));
        List<Surface> surfaces = new ArrayList<>();

        VatFluidRenderer.renderMingledFluid((band, emitter) -> {
            RecordingVertexConsumer recorder = new RecordingVertexConsumer();
            TextureAtlasSprite sprite = spriteOf(band.type());
            emitter.accept(RenderContext.banded(new PoseStack().last(), recorder,
                GooRenderUtil.OPAQUE_WHITE, band), sprite);
            surfaces.add(new Surface(band, sprite, recorder.vertices()));
        }, state);

        assertEquals(List.of(GooTypes.FROST, GooTypes.BLAZE), surfaces.stream().map(s -> s.band().type()).toList());
        for (Surface surface : surfaces) {
            assertTrue(!surface.vertices().isEmpty());
            for (RecordingVertexConsumer.Vertex vertex : surface.vertices()) {
                assertEquals(surface.band().lo(), decode(vertex.uv2U()), 1f / TypeBand.BAND_UNITS);
                assertEquals(surface.band().hi(), decode(vertex.uv2V()), 1f / TypeBand.BAND_UNITS);
                assertTrue(vertex.u() >= surface.sprite().getU0() && vertex.u() <= surface.sprite().getU1());
            }
        }
        RecordingVertexConsumer.Vertex frost = surfaces.get(0).vertices().getFirst();
        RecordingVertexConsumer.Vertex blaze = surfaces.get(1).vertices().getFirst();
        assertEquals(0, frost.uv2U());
        assertEquals(frost.uv2V(), blaze.uv2U());
        assertEquals(TypeBand.BAND_UNITS, blaze.uv2V());
        assertEquals(0.75f, decode(frost.uv2V()));
    }
}
