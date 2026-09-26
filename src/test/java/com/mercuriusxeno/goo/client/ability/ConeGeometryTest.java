package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ConeGeometry.emitCone writes the vertices the metal spike's and the metal
 * dart's own segment emitters wrote before they folded into it, for a table
 * of bases; the reference emitters below transcribe the deleted
 * MetalSpikeVisual.emitConeSegment and BlobFlightRenderer.emitDartSegment
 * (decision render-context-is-the-one-emitter).
 */
class ConeGeometryTest {

    private static final int SIDES = 3;
    private static final float TWO_PI = (float) (2 * Math.PI);
    private static final float HALF = 0.5f;
    private static final float TOLERANCE = 1e-6f;
    private static final int SPIKE_COLOR = 0xCC5080A0;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int LIGHT = 0x00F000F0;
    private static final GooRenderUtil.UvRect UV = new GooRenderUtil.UvRect(0.25f, 0.5f, 0.75f, 0.875f);

    private static final float[][] DIRECTIONS = {
            {1f, 0f, 0f},
            {0f, 1f, 0f},
            {0f, -1f, 0f},
            {0f, 0f, 1f},
            {0.6f, 0.48f, 0.64f},
            {-0.36f, -0.48f, 0.8f},
    };

    /**
     * One expected vertex: position, UV and normal.
     *
     * @param x  the X position
     * @param y  the Y position
     * @param z  the Z position
     * @param u  the texture U
     * @param v  the texture V
     * @param nx the X normal
     * @param ny the Y normal
     * @param nz the Z normal
     */
    private record Expected(float x, float y, float z, float u, float v, float nx, float ny, float nz) {}

    static Stream<float[]> directions() {
        return Stream.of(DIRECTIONS);
    }

    private static List<RecordingVertexConsumer.Vertex> emit(ConeGeometry.Cone cone, float[] basis, int color) {
        RecordingVertexConsumer consumer = new RecordingVertexConsumer();
        ConeGeometry.emitCone(new RenderContext(new PoseStack().last(), consumer, LIGHT),
                cone, basis, SIDES, color, UV);
        return consumer.vertices();
    }

    /** The deleted MetalSpikeVisual.emitConeSegment, every side, transcribed. */
    private static List<Expected> oldSpike(float bx, float by, float bz, float dirX, float dirY, float dirZ,
                                           float length, float radius, float[] basis) {
        float tipX = bx + dirX * length;
        float tipY = by + dirY * length;
        float tipZ = bz + dirZ * length;
        float uMid = (UV.u0() + UV.u1()) * HALF;
        List<Expected> out = new ArrayList<>();
        for (int i = 0; i < SIDES; i++) {
            float a0 = TWO_PI * i / SIDES;
            float a1 = TWO_PI * (i + 1) / SIDES;
            float cos0 = (float) Math.cos(a0) * radius;
            float sin0 = (float) Math.sin(a0) * radius;
            float cos1 = (float) Math.cos(a1) * radius;
            float sin1 = (float) Math.sin(a1) * radius;
            float mid = (a0 + a1) * HALF;
            float midCos = (float) Math.cos(mid);
            float midSin = (float) Math.sin(mid);
            float nx = basis[0] * midCos + basis[3] * midSin;
            float ny = basis[1] * midCos + basis[4] * midSin;
            float nz = basis[2] * midCos + basis[5] * midSin;
            out.add(new Expected(bx + basis[0] * cos0 + basis[3] * sin0, by + basis[1] * cos0 + basis[4] * sin0,
                    bz + basis[2] * cos0 + basis[5] * sin0, UV.u0(), UV.v0(), nx, ny, nz));
            out.add(new Expected(bx + basis[0] * cos1 + basis[3] * sin1, by + basis[1] * cos1 + basis[4] * sin1,
                    bz + basis[2] * cos1 + basis[5] * sin1, UV.u1(), UV.v0(), nx, ny, nz));
            out.add(new Expected(tipX, tipY, tipZ, uMid, UV.v1(), dirX, dirY, dirZ));
            out.add(new Expected(tipX, tipY, tipZ, uMid, UV.v1(), dirX, dirY, dirZ));
        }
        return out;
    }

    /** The deleted BlobFlightRenderer.emitDartSegment, every side, transcribed: base at the origin. */
    private static List<Expected> oldDart(float dirX, float dirY, float dirZ,
                                          float length, float radius, float[] basis) {
        float tipX = dirX * length;
        float tipY = dirY * length;
        float tipZ = dirZ * length;
        float uMid = (UV.u0() + UV.u1()) * HALF;
        List<Expected> out = new ArrayList<>();
        for (int i = 0; i < SIDES; i++) {
            float a0 = TWO_PI * i / SIDES;
            float a1 = TWO_PI * (i + 1) / SIDES;
            float cos0 = (float) Math.cos(a0) * radius;
            float sin0 = (float) Math.sin(a0) * radius;
            float cos1 = (float) Math.cos(a1) * radius;
            float sin1 = (float) Math.sin(a1) * radius;
            float mid = (a0 + a1) * HALF;
            float cosM = (float) Math.cos(mid);
            float sinM = (float) Math.sin(mid);
            float nx = basis[0] * cosM + basis[3] * sinM;
            float ny = basis[1] * cosM + basis[4] * sinM;
            float nz = basis[2] * cosM + basis[5] * sinM;
            out.add(new Expected(basis[0] * cos0 + basis[3] * sin0, basis[1] * cos0 + basis[4] * sin0,
                    basis[2] * cos0 + basis[5] * sin0, UV.u0(), UV.v0(), nx, ny, nz));
            out.add(new Expected(basis[0] * cos1 + basis[3] * sin1, basis[1] * cos1 + basis[4] * sin1,
                    basis[2] * cos1 + basis[5] * sin1, UV.u1(), UV.v0(), nx, ny, nz));
            out.add(new Expected(tipX, tipY, tipZ, uMid, UV.v1(), dirX, dirY, dirZ));
            out.add(new Expected(tipX, tipY, tipZ, uMid, UV.v1(), dirX, dirY, dirZ));
        }
        return out;
    }

    private static void assertVertices(List<Expected> expected, List<RecordingVertexConsumer.Vertex> actual,
                                       int color) {
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            Expected e = expected.get(i);
            RecordingVertexConsumer.Vertex a = actual.get(i);
            String at = "vertex " + i;
            assertEquals(e.x(), a.x(), TOLERANCE, at);
            assertEquals(e.y(), a.y(), TOLERANCE, at);
            assertEquals(e.z(), a.z(), TOLERANCE, at);
            assertEquals(e.u(), a.u(), TOLERANCE, at);
            assertEquals(e.v(), a.v(), TOLERANCE, at);
            assertEquals(e.nx(), a.nx(), TOLERANCE, at);
            assertEquals(e.ny(), a.ny(), TOLERANCE, at);
            assertEquals(e.nz(), a.nz(), TOLERANCE, at);
            assertEquals(color, a.color(), at);
        }
    }

    @ParameterizedTest
    @MethodSource("directions")
    void spikeConeMatchesTheOldSpikeSegments(float[] dir) {
        float[] basis = ConeGeometry.computeBasis(dir[0], dir[1], dir[2]);
        ConeGeometry.Cone cone = new ConeGeometry.Cone(0.5f, 0f, 0.5f, dir[0], dir[1], dir[2], 1.3f, 0.104f);

        assertVertices(oldSpike(0.5f, 0f, 0.5f, dir[0], dir[1], dir[2], 1.3f, 0.104f, basis),
                emit(cone, basis, SPIKE_COLOR), SPIKE_COLOR);
    }

    @ParameterizedTest
    @MethodSource("directions")
    void dartFrontMatchesTheOldDartSegments(float[] dir) {
        float[] basis = ConeGeometry.computeBasis(dir[0], dir[1], dir[2]);
        ConeGeometry.Cone cone = new ConeGeometry.Cone(0f, 0f, 0f, dir[0], dir[1], dir[2], 2.5f, 0.05f);

        assertVertices(oldDart(dir[0], dir[1], dir[2], 2.5f, 0.05f, basis), emit(cone, basis, WHITE), WHITE);
    }

    @ParameterizedTest
    @MethodSource("directions")
    void dartRearKeepsTheFrontBasis(float[] dir) {
        float[] frontBasis = ConeGeometry.computeBasis(dir[0], dir[1], dir[2]);
        ConeGeometry.Cone rear = new ConeGeometry.Cone(0f, 0f, 0f, -dir[0], -dir[1], -dir[2], 0.5f, 0.09f);

        assertVertices(oldDart(-dir[0], -dir[1], -dir[2], 0.5f, 0.09f, frontBasis),
                emit(rear, frontBasis, WHITE), WHITE);
    }

    @Test
    void triangleEmitsBothBaseCornersThenTheApexTwice() {
        List<Integer> corners = new ArrayList<>();

        ConeGeometry.emitTriangle(corners::add);

        assertEquals(List.of(ConeGeometry.BASE_START, ConeGeometry.BASE_END, ConeGeometry.APEX, ConeGeometry.APEX),
                corners);
    }
}
