package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.GooTypes;
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
    void aPouringTapEmitsOneThinColumnFromSpigotToSurface() {
        // BlockEntityRenderState's constructor bootstraps Blocks, so the state is built without it.
        TapRenderState state = mock(TapRenderState.class);
        state.streamType = GooTypes.BLAZE;
        state.streamBottomY = LANDING_LOCAL_Y;

        List<RecordingVertexConsumer.Vertex> vertices = emit(state);

        assertEquals(SIDES * VERTICES_PER_QUAD, vertices.size());
        assertTrue(vertices.stream().allMatch(vertex -> vertex.color() == TINT));
        assertEquals(LANDING_LOCAL_Y, vertices.stream().map(RecordingVertexConsumer.Vertex::y)
                .min(Float::compare).orElseThrow());
        assertEquals((float) TapStream.SPIGOT_UNDERSIDE_LOCAL_Y, vertices.stream()
                .map(RecordingVertexConsumer.Vertex::y).max(Float::compare).orElseThrow());
        assertTrue(vertices.stream().allMatch(vertex -> Math.abs(vertex.x() - HALF_BLOCK) < THIN
                && Math.abs(vertex.z() - HALF_BLOCK) < THIN));
    }

    @Test
    void aDrippingTapEmitsNoColumn() {
        TapRenderState state = mock(TapRenderState.class);
        state.streamType = null;

        assertTrue(emit(state).isEmpty());
    }
}
