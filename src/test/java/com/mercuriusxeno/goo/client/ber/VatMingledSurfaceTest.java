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
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests that a vat holding two goo types submits one surface per type, each
 * on its own type's sprite, every vertex carrying that type's share and
 * layer, and the second layer lifted outward of the first.
 */
class VatMingledSurfaceTest {

    /** Each type's sprite sits in its own quarter of the atlas along U. */
    private static final Map<ResourceKey<GooTypeDefinition>, Float> SPRITE_U0 =
        Map.of(GooTypes.FROST, 0f, GooTypes.BLAZE, 0.5f);
    private static final float SPRITE_WIDTH = 0.25f;
    private static final float POSITION_TOLERANCE = 1e-6f;

    private record Surface(TypeBand band, TextureAtlasSprite sprite, List<RecordingVertexConsumer.Vertex> vertices) {

        double extreme(Predicate<RecordingVertexConsumer.Vertex> face,
                       ToDoubleFunction<RecordingVertexConsumer.Vertex> axis, boolean highest) {
            var values = vertices.stream().filter(face).mapToDouble(axis);
            return highest ? values.max().orElseThrow() : values.min().orElseThrow();
        }
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

    private static List<Surface> renderTwoTypes() {
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
        return surfaces;
    }

    @Test
    void twoTypesSubmitOneLayerEachCarryingShareAndLayerOnTheirOwnSprites() {
        List<Surface> surfaces = renderTwoTypes();

        assertEquals(List.of(GooTypes.FROST, GooTypes.BLAZE), surfaces.stream().map(s -> s.band().type()).toList());
        List<List<Integer>> shareAndLayer = List.of(
            List.of(TypeBand.SHARE_UNITS, 0), List.of(TypeBand.SHARE_UNITS / 4, 1));
        for (int k = 0; k < surfaces.size(); k++) {
            Surface surface = surfaces.get(k);
            assertFalse(surface.vertices().isEmpty());
            for (RecordingVertexConsumer.Vertex vertex : surface.vertices()) {
                assertEquals(shareAndLayer.get(k), List.of(vertex.uv2U(), vertex.uv2V()));
                assertTrue(vertex.u() >= surface.sprite().getU0() && vertex.u() <= surface.sprite().getU1());
            }
        }
    }

    @Test
    void theSecondLayerSitsOneLiftOutwardOfTheFirst() {
        List<Surface> surfaces = renderTwoTypes();
        Surface base = surfaces.get(0);
        Surface over = surfaces.get(1);
        Predicate<RecordingVertexConsumer.Vertex> top = vertex -> vertex.ny() > 0f;
        Predicate<RecordingVertexConsumer.Vertex> underside = vertex -> vertex.ny() < 0f;
        Predicate<RecordingVertexConsumer.Vertex> side = vertex -> vertex.ny() == 0f;
        float lift = TypeBand.LAYER_LIFT;

        assertEquals(base.extreme(top, RecordingVertexConsumer.Vertex::y, true) + lift,
            over.extreme(top, RecordingVertexConsumer.Vertex::y, true), POSITION_TOLERANCE);
        assertEquals(base.extreme(underside, RecordingVertexConsumer.Vertex::y, true) - lift,
            over.extreme(underside, RecordingVertexConsumer.Vertex::y, true), POSITION_TOLERANCE);
        assertEquals(base.extreme(side, RecordingVertexConsumer.Vertex::x, false) - lift,
            over.extreme(side, RecordingVertexConsumer.Vertex::x, false), POSITION_TOLERANCE);
        assertEquals(base.extreme(side, RecordingVertexConsumer.Vertex::z, true) + lift,
            over.extreme(side, RecordingVertexConsumer.Vertex::z, true), POSITION_TOLERANCE);
        assertEquals(base.extreme(side, RecordingVertexConsumer.Vertex::y, true) + lift,
            over.extreme(side, RecordingVertexConsumer.Vertex::y, true), POSITION_TOLERANCE);
    }
}
