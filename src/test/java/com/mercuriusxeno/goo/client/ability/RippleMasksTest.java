package com.mercuriusxeno.goo.client.ability;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A silhouette's mask fills each grown cube whole, all six faces, so the
 * cubes' union covers the grown body's projection with no seam
 * (decision afterimage-is-one-shared-effect).
 */
class RippleMasksTest {

    private static final float EPSILON = 1e-5f;

    @Test
    void growthMovesEveryFaceOut() {
        Vector3f[] grown = RippleMasks.grownCorners(new Vector3f(0, 0, 0), new Vector3f(1, 2, 3), 0.25f);

        assertEquals(new Vector3f(-0.25f, -0.25f, -0.25f), grown[0]);
        assertEquals(new Vector3f(1.25f, 2.25f, 3.25f), grown[RippleMasks.CORNERS - 1]);
    }

    @Test
    void sixFacesCoverEveryCornerOfTheBoxThreeTimes() {
        int[] uses = new int[RippleMasks.CORNERS];
        Set<String> faces = new HashSet<>();
        for (int axis = 0; axis < 3; axis++) {
            for (boolean high : new boolean[] {false, true}) {
                int[] face = RippleMasks.faceCorners(axis, high);
                Set<Integer> distinct = new HashSet<>();
                for (int corner : face) {
                    uses[corner]++;
                    distinct.add(corner);
                }
                assertEquals(4, distinct.size(), "a face repeats a corner");
                faces.add(distinct.toString());
            }
        }

        assertEquals(6, faces.size());
        for (int use : uses) {
            assertEquals(3, use);
        }
    }

    @Test
    void aFaceIsFlatAcrossItsAxis() {
        Vector3f[] corners = RippleMasks.grownCorners(new Vector3f(0, 0, 0), new Vector3f(1, 1, 1), 0f);

        for (int corner : RippleMasks.faceCorners(1, true)) {
            assertEquals(1f, corners[corner].y, EPSILON);
        }
    }

    @Test
    void aFilledCubeLaysTwentyFourVerticesThroughThePose() {
        List<Vector3f> laid = new ArrayList<>();
        VertexConsumer buffer = mock(VertexConsumer.class);
        when(buffer.addVertex(anyFloat(), anyFloat(), anyFloat())).thenAnswer(call -> {
            laid.add(new Vector3f((float) call.getArgument(0), (float) call.getArgument(1), (float) call.getArgument(2)));
            return buffer;
        });
        when(buffer.setColor(anyInt())).thenReturn(buffer);

        RippleMasks.fillGrownCube(buffer, new Matrix4f().translation(10, 0, 0), new Vector3f(0, 0, 0),
                new Vector3f(1, 1, 1), 0.5f, 0x80FFFFFF);

        assertEquals(24, laid.size());
        for (Vector3f vertex : laid) {
            assertEquals(1f, Math.abs(vertex.x - 10.5f), EPSILON, "a vertex is not on the grown, moved box");
        }
    }
}
