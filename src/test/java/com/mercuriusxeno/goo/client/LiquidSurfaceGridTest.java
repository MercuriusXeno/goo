package com.mercuriusxeno.goo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the rippling liquid surface grid RenderContext emits: its vertex
 * count, a still rim at the fill height, and the resting amplitude on
 * every interior vertex.
 */
class LiquidSurfaceGridTest {

    private static final float FILL_HEIGHT = 0.6f;
    private static final CuboidBounds BOX = new CuboidBounds(0.125f, 0.875f, 0.125f, 0.875f, 0.1f, FILL_HEIGHT);
    private static final GooRenderUtil.UvRect UV = new GooRenderUtil.UvRect(0.25f, 0.5f, 0.5f, 0.75f);
    private static final int CELLS = RenderContext.SURFACE_GRID_CELLS;
    private static final float POSITION_TOLERANCE = 1e-6f;

    private static List<RecordingVertexConsumer.Vertex> emitUp(float amplitude) {
        RecordingVertexConsumer recorder = new RecordingVertexConsumer();
        new RenderContext(new PoseStack().last(), recorder, 0).liquidSurfaceGrid(BOX, UV, amplitude);
        return recorder.vertices();
    }

    private static List<RecordingVertexConsumer.Vertex> emitDown(float amplitude) {
        RecordingVertexConsumer recorder = new RecordingVertexConsumer();
        new RenderContext(new PoseStack().last(), recorder, 0).liquidSurfaceGridDown(BOX, UV, amplitude);
        return recorder.vertices();
    }

    private static boolean isOnRim(RecordingVertexConsumer.Vertex vertex) {
        return near(vertex.x(), BOX.x0()) || near(vertex.x(), BOX.x1())
            || near(vertex.z(), BOX.z0()) || near(vertex.z(), BOX.z1());
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < POSITION_TOLERANCE;
    }

    @Test
    void gridEmitsFourVerticesPerCell() {
        assertEquals(CELLS * CELLS * 4, emitUp(RenderContext.RESTING_RIPPLE_AMPLITUDE).size());
    }

    @Test
    void everyVertexSitsAtTheFillHeight() {
        for (RecordingVertexConsumer.Vertex vertex : emitUp(RenderContext.RESTING_RIPPLE_AMPLITUDE)) {
            assertEquals(FILL_HEIGHT, vertex.y(), POSITION_TOLERANCE);
        }
    }

    @Test
    void rimVerticesCarryZeroAmplitude() {
        long rimCount = 0;
        for (RecordingVertexConsumer.Vertex vertex : emitUp(RenderContext.RESTING_RIPPLE_AMPLITUDE)) {
            if (isOnRim(vertex)) {
                assertEquals(0, vertex.uv1U(), "rim vertex at " + vertex);
                rimCount++;
            }
        }
        assertTrue(rimCount > 0);
    }

    @Test
    void interiorVerticesCarryTheRestingAmplitude() {
        int resting = RenderContext.encodeAmplitude(RenderContext.RESTING_RIPPLE_AMPLITUDE);
        assertTrue(resting > 0);
        long interiorCount = 0;
        for (RecordingVertexConsumer.Vertex vertex : emitUp(RenderContext.RESTING_RIPPLE_AMPLITUDE)) {
            if (!isOnRim(vertex)) {
                assertEquals(resting, vertex.uv1U(), "interior vertex at " + vertex);
                interiorCount++;
            }
        }
        // Each of the (CELLS - 1)^2 interior grid points is a corner of four cells.
        assertEquals((long) (CELLS - 1) * (CELLS - 1) * 4, interiorCount);
    }

    @Test
    void gridSpansTheBoundsAndTheUvRect() {
        List<RecordingVertexConsumer.Vertex> vertices = emitUp(RenderContext.RESTING_RIPPLE_AMPLITUDE);
        assertTrue(vertices.stream().anyMatch(vt -> vt.x() == BOX.x0() && vt.z() == BOX.z0()
            && vt.u() == UV.u0() && vt.v() == UV.v0()));
        assertTrue(vertices.stream().anyMatch(vt -> vt.x() == BOX.x1() && vt.z() == BOX.z1()
            && vt.u() == UV.u1() && vt.v() == UV.v1()));
    }

    @Test
    void underSideMirrorsTheTopWithADownwardNormal() {
        List<RecordingVertexConsumer.Vertex> down = emitDown(RenderContext.RESTING_RIPPLE_AMPLITUDE);
        assertEquals(CELLS * CELLS * 4, down.size());
        int resting = RenderContext.encodeAmplitude(RenderContext.RESTING_RIPPLE_AMPLITUDE);
        for (RecordingVertexConsumer.Vertex vertex : down) {
            assertEquals(-1f, vertex.ny());
            assertEquals(isOnRim(vertex) ? 0 : resting, vertex.uv1U());
        }
    }

    @Test
    void anAgitatedAmplitudeReachesInteriorVerticesOnly() {
        float agitated = RenderContext.RESTING_RIPPLE_AMPLITUDE + SurfaceAgitation.AGITATION_CEILING;
        int encoded = RenderContext.encodeAmplitude(agitated);
        assertTrue(encoded > RenderContext.encodeAmplitude(RenderContext.RESTING_RIPPLE_AMPLITUDE));
        for (RecordingVertexConsumer.Vertex vertex : emitUp(agitated)) {
            assertEquals(isOnRim(vertex) ? 0 : encoded, vertex.uv1U(), "vertex at " + vertex);
        }
    }

    @Test
    void amplitudeEncodesInShaderUnits() {
        assertEquals(RenderContext.AMPLITUDE_UNITS_PER_BLOCK / 4, RenderContext.encodeAmplitude(0.25f));
        assertEquals(0, RenderContext.encodeAmplitude(-1f));
        assertEquals(Short.MAX_VALUE, RenderContext.encodeAmplitude(100f));
    }
}
