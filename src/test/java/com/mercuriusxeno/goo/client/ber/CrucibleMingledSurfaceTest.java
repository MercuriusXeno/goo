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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests that a crucible submits one surface per goo type it shows, the
 * bands complementary across the surfaces, and a single type one surface
 * spanning the whole band.
 */
class CrucibleMingledSurfaceTest {

    private static final float SURFACE_Y = 0.875f;

    private static TextureAtlasSprite sprite() {
        TextureAtlasSprite sprite = mock(TextureAtlasSprite.class);
        when(sprite.getU1()).thenReturn(1f);
        when(sprite.getV1()).thenReturn(1f);
        return sprite;
    }

    static Stream<Arguments> contentsAndSurfaceBandEdges() {
        return Stream.of(
            Arguments.of(Map.of(GooTypes.BLAZE, 700),
                List.of(List.of(0, TypeBand.BAND_UNITS))),
            Arguments.of(Map.of(GooTypes.BLAZE, 250, GooTypes.FROST, 750),
                List.of(List.of(0, TypeBand.BAND_UNITS * 3 / 4),
                    List.of(TypeBand.BAND_UNITS * 3 / 4, TypeBand.BAND_UNITS))));
    }

    @ParameterizedTest
    @MethodSource("contentsAndSurfaceBandEdges")
    void renderMingledSurface(Map<ResourceKey<GooTypeDefinition>, Integer> volumes,
                              List<List<Integer>> bandEdgesPerSurface) {
        // BlockEntityRenderState's constructor bootstraps Blocks, so the state is built without it.
        CrucibleRenderState state = mock(CrucibleRenderState.class);
        state.typeBands = TypeBands.over(new GooContents(volumes));
        state.rippleAmplitude = RenderContext.RESTING_RIPPLE_AMPLITUDE;
        List<List<RecordingVertexConsumer.Vertex>> surfaces = new ArrayList<>();

        CrucibleBlockEntityRenderer.renderMingledSurface((band, emitter) -> {
            RecordingVertexConsumer recorder = new RecordingVertexConsumer();
            emitter.accept(RenderContext.banded(new PoseStack().last(), recorder,
                GooRenderUtil.OPAQUE_WHITE, band), sprite());
            surfaces.add(recorder.vertices());
        }, state, SURFACE_Y);

        assertEquals(bandEdgesPerSurface.size(), surfaces.size());
        for (int i = 0; i < surfaces.size(); i++) {
            List<RecordingVertexConsumer.Vertex> vertices = surfaces.get(i);
            assertFalse(vertices.isEmpty());
            for (RecordingVertexConsumer.Vertex vertex : vertices) {
                assertEquals(bandEdgesPerSurface.get(i), List.of(vertex.uv2U(), vertex.uv2V()));
                assertEquals(SURFACE_Y, vertex.y(), 1e-6f);
            }
        }
    }
}
