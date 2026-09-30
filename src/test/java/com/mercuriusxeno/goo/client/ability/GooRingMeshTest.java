package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import net.minecraft.core.Direction;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The goo ring particle's disc: its rim in the face's plane at its radius,
 * its progress over the life, its tint, and the shader pair its pipeline names.
 */
class GooRingMeshTest {

    private static final float TOLERANCE = 1e-5f;
    private static final float RADIUS = 1.5f;
    private static final int LIFETIME = 10;
    private static final int TINT = 0xC2A868;
    private static final int ALPHA_SHIFT = 24;
    private static final int BYTE = 0xFF;
    private static final int RGB = 0xFFFFFF;

    @ParameterizedTest
    @EnumSource(Direction.class)
    void rimLiesInTheFacesPlaneAtTheRadius(Direction face) {
        List<Vector3f> rim = GooRingMesh.rim(face, RADIUS);

        assertEquals(GooRingMesh.SEGMENTS, rim.size());
        for (Vector3f point : rim) {
            double along = face.getAxis().choose(point.x(), point.y(), point.z());
            assertEquals(0.0, along, 0.0, point + " leaves the plane square to " + face);
            assertEquals(RADIUS, point.length(), TOLERANCE, point + " is off the radius");
        }
        Vector3f first = rim.getFirst();
        Vector3f opposite = rim.get(GooRingMesh.SEGMENTS / 2);
        assertEquals(2 * RADIUS, first.distance(opposite), TOLERANCE, "the rim does not run the whole way round");
    }

    @Test
    void progressByteRunsFromNothingToFullOverTheLife() {
        int previous = -1;
        for (int age = 0; age <= LIFETIME; age++) {
            int progressByte = GooRingMesh.vertexColor(TINT, GooRingMesh.progress(age, 0f, LIFETIME)) >>> ALPHA_SHIFT;
            assertTrue(progressByte > previous, "progress stalls at age " + age);
            previous = progressByte;
        }
        assertEquals(0, GooRingMesh.vertexColor(TINT, GooRingMesh.progress(0, 0f, LIFETIME)) >>> ALPHA_SHIFT);
        assertEquals(BYTE, GooRingMesh.vertexColor(TINT, GooRingMesh.progress(LIFETIME, 0f, LIFETIME)) >>> ALPHA_SHIFT);
        assertEquals(BYTE, GooRingMesh.vertexColor(TINT, GooRingMesh.progress(LIFETIME + 3, 0f, LIFETIME))
                >>> ALPHA_SHIFT, "progress overruns past the life");
    }

    @Test
    void vertexColorCarriesTheTint() {
        assertEquals(TINT, GooRingMesh.vertexColor(TINT, 0.4f) & RGB);
        assertEquals(TINT, GooRingMesh.vertexColor(TINT | 0x7F000000, 0.4f) & RGB);
    }

    @Test
    void pipelineShadersResolveOnTheClasspath() {
        PipelineShaders.assertExist(GooRenderTypes.GOO_RING);
    }
}
