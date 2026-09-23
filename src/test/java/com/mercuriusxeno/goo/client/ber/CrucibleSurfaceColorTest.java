package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Tests that the crucible's packed crossfade color reaches every vertex of
 * its rippling surface grid unchanged.
 */
class CrucibleSurfaceColorTest {

    private static final int TINT = 0xFF3F76E4;
    private static final float SURFACE_Y = 0.875f;

    @Test
    void crossfadeColorReachesEveryGridVertexUnchanged() {
        int crossfade = CrucibleBlockEntityRenderer.packArgb(0.4f, TINT);
        RecordingVertexConsumer recorder = new RecordingVertexConsumer();
        RenderContext ctx = new RenderContext(new PoseStack().last(), recorder, 0, crossfade);
        CrucibleBlockEntityRenderer.emitLiquidSurface(ctx, SURFACE_Y, new GooRenderUtil.UvRect(0f, 0f, 1f, 1f));
        List<RecordingVertexConsumer.Vertex> vertices = recorder.vertices();
        assertFalse(vertices.isEmpty());
        for (RecordingVertexConsumer.Vertex vertex : vertices) {
            assertEquals(crossfade, vertex.color());
            assertEquals(SURFACE_Y, vertex.y(), 1e-6f);
        }
    }

    @Test
    void packedCrossfadeKeepsTheTintAndCarriesTheAlpha() {
        int crossfade = CrucibleBlockEntityRenderer.packArgb(0.4f, TINT);
        assertEquals(TINT & 0xFFFFFF, crossfade & 0xFFFFFF);
        assertEquals((int) (0.4f * 255), crossfade >>> 24);
    }
}
