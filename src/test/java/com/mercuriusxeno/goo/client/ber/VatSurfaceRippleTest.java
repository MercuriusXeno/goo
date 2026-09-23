package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mercuriusxeno.goo.client.SurfaceAgitation;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests that the ripple amplitude a vat's render state carries reaches the
 * interior of its surface grids while the rim and the side faces stay still.
 */
class VatSurfaceRippleTest {

    private static TextureAtlasSprite sprite() {
        TextureAtlasSprite sprite = mock(TextureAtlasSprite.class);
        when(sprite.getU0()).thenReturn(0f);
        when(sprite.getU1()).thenReturn(1f);
        when(sprite.getV0()).thenReturn(0f);
        when(sprite.getV1()).thenReturn(1f);
        return sprite;
    }

    @Test
    void theStateAmplitudeReachesTheSurfaceInteriorOnly() {
        // BlockEntityRenderState's constructor bootstraps Blocks, so the state is built without it.
        VatRenderState state = mock(VatRenderState.class);
        state.stackSize = 1;
        state.fillFraction = 0.5f;
        state.rippleAmplitude = RenderContext.RESTING_RIPPLE_AMPLITUDE + SurfaceAgitation.AGITATION_CEILING;
        RecordingVertexConsumer recorder = new RecordingVertexConsumer();

        VatFluidRenderer.renderFluid(new RenderContext(new PoseStack().last(), recorder, 0), sprite(), state);

        int encoded = RenderContext.encodeAmplitude(state.rippleAmplitude);
        int cells = RenderContext.SURFACE_GRID_CELLS;
        int gridVertices = cells * cells * 4;
        List<RecordingVertexConsumer.Vertex> vertices = recorder.vertices();
        assertEquals(2 * gridVertices + 4 * 4, vertices.size());
        long interior = vertices.stream().filter(vertex -> vertex.uv1U() == encoded).count();
        long still = vertices.stream().filter(vertex -> vertex.uv1U() == 0).count();
        // Top and underside each hold (cells - 1)^2 interior points shared by four cells.
        assertEquals(2L * (cells - 1) * (cells - 1) * 4, interior);
        assertEquals(vertices.size() - interior, still);
        assertTrue(vertices.subList(2 * gridVertices, vertices.size()).stream()
            .allMatch(vertex -> vertex.uv1U() == 0));
    }
}
