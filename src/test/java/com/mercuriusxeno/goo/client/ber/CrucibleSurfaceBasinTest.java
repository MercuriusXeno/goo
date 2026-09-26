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
        assertEquals(CrucibleBasin.FLOOR_Y, CrucibleBasin.surfaceYForVolume(0), EPSILON);
    }

    @Test
    void fullVolumeSurfaceReachesTheRim() {
        assertEquals(CrucibleBasin.RIM_Y,
            CrucibleBasin.surfaceYForVolume(CrucibleBasin.RIM_VOLUME), EPSILON);
    }

    @ParameterizedTest
    @ValueSource(ints = {50, 500, 8_000_000})
    void everySurfaceVertexLiesInsideTheGoosFootprint(int volume) {
        CrucibleBasin.PuddleFootprint footprint = CrucibleBasin.footprintForVolume(volume);
        List<RecordingVertexConsumer.Vertex> vertices = emitAt(volume, RenderContext.RESTING_RIPPLE_AMPLITUDE);
        assertFalse(vertices.isEmpty());
        for (RecordingVertexConsumer.Vertex vertex : vertices) {
            assertInside(footprint, vertex.x());
            assertInside(footprint, vertex.z());
            assertTrue(vertex.y() > CrucibleBasin.FLOOR_Y && vertex.y() <= CrucibleBasin.RIM_Y,
                "vertex y " + vertex.y() + " left the floor-to-rim span");
        }
    }

    @Test
    void puddleAtHalfTheSpreadDrawsStrictlyInsideTheWalls() {
        List<RecordingVertexConsumer.Vertex> vertices = emitAt(CrucibleBasin.SPREAD_VOLUME / 2, 0f);
        assertTrue(vertices.stream().allMatch(vertex -> vertex.x() > CrucibleBasin.FOOTPRINT_MIN
                && vertex.x() < CrucibleBasin.FOOTPRINT_MAX
                && vertex.z() > CrucibleBasin.FOOTPRINT_MIN && vertex.z() < CrucibleBasin.FOOTPRINT_MAX),
            "a vertex of the 500 mB puddle reached the walls");
    }

    @Test
    void surfaceGridCoversTheWholeFootprintOnceSpread() {
        List<RecordingVertexConsumer.Vertex> vertices = emitAt(CrucibleBasin.SPREAD_VOLUME, 0f);
        assertEquals(CrucibleBasin.FOOTPRINT_MIN,
            (float) vertices.stream().mapToDouble(RecordingVertexConsumer.Vertex::x).min().orElseThrow(), EPSILON);
        assertEquals(CrucibleBasin.FOOTPRINT_MAX,
            (float) vertices.stream().mapToDouble(RecordingVertexConsumer.Vertex::x).max().orElseThrow(), EPSILON);
    }

    private static List<RecordingVertexConsumer.Vertex> emitAt(int volume, float amplitude) {
        RecordingVertexConsumer recorder = new RecordingVertexConsumer();
        RenderContext ctx = new RenderContext(new PoseStack().last(), recorder, 0, 0xFFFFFFFF);
        CrucibleBlockEntityRenderer.emitLiquidSurface(ctx, CrucibleBasin.footprintForVolume(volume),
            CrucibleBasin.surfaceYForVolume(volume), new GooRenderUtil.UvRect(0f, 0f, 1f, 1f), amplitude);
        return recorder.vertices();
    }

    private static void assertInside(CrucibleBasin.PuddleFootprint footprint, float coordinate) {
        assertTrue(coordinate >= footprint.min() - EPSILON && coordinate <= footprint.max() + EPSILON,
            "vertex coordinate " + coordinate + " left the footprint " + footprint);
    }
}
