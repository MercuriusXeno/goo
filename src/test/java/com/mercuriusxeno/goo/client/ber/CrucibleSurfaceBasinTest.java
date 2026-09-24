package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.block.crucible.CrucibleBasin;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that the crucible surface is drawn over the basin footprint and
 * between the basin's floor and rim at every reservoir volume.
 */
class CrucibleSurfaceBasinTest {

    private static final float EPSILON = 1e-6f;

    @Test
    void emptyVolumeSurfaceSitsOnTheFloor() {
        assertEquals(CrucibleBasin.FLOOR_Y, CrucibleBlockEntityRenderer.surfaceYForVolume(0), EPSILON);
    }

    @Test
    void fullVolumeSurfaceReachesTheRim() {
        assertEquals(CrucibleBasin.RIM_Y,
            CrucibleBlockEntityRenderer.surfaceYForVolume(CrucibleBlockEntityRenderer.LIQUID_LOG_CAP), EPSILON);
    }

    @ParameterizedTest
    @ValueSource(ints = {50, 60_000})
    void everySurfaceVertexLiesInsideTheBasin(int volume) {
        float surfaceY = CrucibleBlockEntityRenderer.surfaceYForVolume(volume);
        RecordingVertexConsumer recorder = new RecordingVertexConsumer();
        RenderContext ctx = new RenderContext(new PoseStack().last(), recorder, 0, 0xFFFFFFFF);
        CrucibleBlockEntityRenderer.emitLiquidSurface(ctx, surfaceY, new GooRenderUtil.UvRect(0f, 0f, 1f, 1f),
            RenderContext.RESTING_RIPPLE_AMPLITUDE);
        List<RecordingVertexConsumer.Vertex> vertices = recorder.vertices();
        assertFalse(vertices.isEmpty());
        for (RecordingVertexConsumer.Vertex vertex : vertices) {
            assertInsideFootprint(vertex.x());
            assertInsideFootprint(vertex.z());
            assertTrue(vertex.y() >= CrucibleBasin.FLOOR_Y && vertex.y() <= CrucibleBasin.RIM_Y,
                "vertex y " + vertex.y() + " left the floor-to-rim span");
        }
    }

    @Test
    void surfaceGridCoversTheWholeFootprint() {
        RecordingVertexConsumer recorder = new RecordingVertexConsumer();
        RenderContext ctx = new RenderContext(new PoseStack().last(), recorder, 0, 0xFFFFFFFF);
        CrucibleBlockEntityRenderer.emitLiquidSurface(ctx, CrucibleBasin.FLOOR_Y,
            new GooRenderUtil.UvRect(0f, 0f, 1f, 1f), 0f);
        List<RecordingVertexConsumer.Vertex> vertices = recorder.vertices();
        assertEquals(CrucibleBasin.FOOTPRINT_MIN,
            (float) vertices.stream().mapToDouble(RecordingVertexConsumer.Vertex::x).min().orElseThrow(), EPSILON);
        assertEquals(CrucibleBasin.FOOTPRINT_MAX,
            (float) vertices.stream().mapToDouble(RecordingVertexConsumer.Vertex::x).max().orElseThrow(), EPSILON);
    }

    private static void assertInsideFootprint(float coordinate) {
        assertTrue(coordinate >= CrucibleBasin.FOOTPRINT_MIN - EPSILON
                && coordinate <= CrucibleBasin.FOOTPRINT_MAX + EPSILON,
            "vertex coordinate " + coordinate + " left the footprint");
    }
}
