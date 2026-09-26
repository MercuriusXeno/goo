package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.block.tap.TapDripGrade;
import com.mercuriusxeno.goo.block.tap.TapStream;
import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The tap renderer emits a thin column of the goo's tint from the spigot
 * underside to the landing surface while the tap pours a stream, and
 * nothing while it drips.
 */
class TapStreamColumnTest {

    private static final int TINT = 0xFFCC5500;
    /** The landing surface two blocks below the tap's block floor. */
    private static final float LANDING_LOCAL_Y = -2f;
    private static final int SIDES = 4;
    private static final int VERTICES_PER_QUAD = 4;
    private static final float HALF_BLOCK = 0.5f;
    private static final float THIN = 1f / 16f;
    private static final int THREE_BLOCKS = 3;
    private static final float SPAN_TOLERANCE = 1e-4f;

    private static TextureAtlasSprite sprite() {
        TextureAtlasSprite sprite = mock(TextureAtlasSprite.class);
        when(sprite.getU0()).thenReturn(0f);
        when(sprite.getU1()).thenReturn(1f);
        when(sprite.getV0()).thenReturn(0f);
        when(sprite.getV1()).thenReturn(1f);
        return sprite;
    }

    private static List<RecordingVertexConsumer.Vertex> emit(TapRenderState state) {
        RecordingVertexConsumer recorder = new RecordingVertexConsumer();
        TapBlockEntityRenderer.emitStream(new RenderContext(new PoseStack().last(), recorder, 0), state, sprite(),
                TINT);
        return recorder.vertices();
    }

    @Test
    void aPouringTapEmitsAThinColumnFromSpigotToSurface() {
        // BlockEntityRenderState's constructor bootstraps Blocks, so the state is built without it.
        TapRenderState state = mock(TapRenderState.class);
        state.streamType = GooTypes.BLAZE;
        state.streamBottomY = LANDING_LOCAL_Y;

        List<RecordingVertexConsumer.Vertex> vertices = emit(state);

        int segments = (int) Math.ceil(TapStream.SPIGOT_UNDERSIDE_LOCAL_Y - LANDING_LOCAL_Y);
        assertEquals(segments * SIDES * VERTICES_PER_QUAD, vertices.size());
        assertTrue(vertices.stream().allMatch(vertex -> vertex.color() == TINT));
        assertEquals(LANDING_LOCAL_Y, vertices.stream().map(RecordingVertexConsumer.Vertex::y)
                .min(Float::compare).orElseThrow());
        assertEquals((float) TapStream.SPIGOT_UNDERSIDE_LOCAL_Y, vertices.stream()
                .map(RecordingVertexConsumer.Vertex::y).max(Float::compare).orElseThrow());
        assertTrue(vertices.stream().allMatch(vertex -> Math.abs(vertex.x() - HALF_BLOCK) < THIN
                && Math.abs(vertex.z() - HALF_BLOCK) < THIN));
    }

    @Test
    void aThreeBlockColumnTilesOneSpritePerBlock() {
        TapRenderState state = mock(TapRenderState.class);
        state.streamType = GooTypes.BLAZE;
        state.streamBottomY = (float) TapStream.SPIGOT_UNDERSIDE_LOCAL_Y - THREE_BLOCKS;

        List<RecordingVertexConsumer.Vertex> vertices = emit(state);

        int quads = vertices.size() / VERTICES_PER_QUAD;
        StringBuilder spans = new StringBuilder();
        for (int quad = 0; quad < quads; quad++) {
            List<RecordingVertexConsumer.Vertex> corners =
                    vertices.subList(quad * VERTICES_PER_QUAD, (quad + 1) * VERTICES_PER_QUAD);
            spans.append(String.format(" [y %.3f v %.3f]", span(corners, RecordingVertexConsumer.Vertex::y),
                    span(corners, RecordingVertexConsumer.Vertex::v)));
        }
        assertEquals(THREE_BLOCKS * SIDES, quads, "quads, with y and v span each:" + spans);
        for (int quad = 0; quad < quads; quad++) {
            List<RecordingVertexConsumer.Vertex> corners =
                    vertices.subList(quad * VERTICES_PER_QUAD, (quad + 1) * VERTICES_PER_QUAD);
            assertEquals(1f, span(corners, RecordingVertexConsumer.Vertex::y), SPAN_TOLERANCE, spans.toString());
            assertEquals(1f, span(corners, RecordingVertexConsumer.Vertex::v), SPAN_TOLERANCE, spans.toString());
        }
    }

    private static float span(List<RecordingVertexConsumer.Vertex> corners,
                              java.util.function.ToDoubleFunction<RecordingVertexConsumer.Vertex> axis) {
        double min = corners.stream().mapToDouble(axis).min().orElseThrow();
        double max = corners.stream().mapToDouble(axis).max().orElseThrow();
        return (float) (max - min);
    }

    @Test
    void aOneToFourTapPoursTheStreamColumnAsOneToOneDoes() {
        for (TapDripGrade grade : List.of(TapDripGrade.ONE_PER_TICK, TapDripGrade.FOUR_PER_TICK)) {
            TapRenderState state = mock(TapRenderState.class);
            state.streamType = grade.pours() ? GooTypes.BLAZE : null;
            state.streamBottomY = LANDING_LOCAL_Y;

            assertTrue(!emit(state).isEmpty(), grade.name() + " pours a stream column");
        }
    }

    @Test
    void aDrippingTapEmitsNoColumn() {
        TapRenderState state = mock(TapRenderState.class);
        state.streamType = null;

        assertTrue(emit(state).isEmpty());
    }
}
