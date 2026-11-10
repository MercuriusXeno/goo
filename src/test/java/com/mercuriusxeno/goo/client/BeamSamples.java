package com.mercuriusxeno.goo.client;

import java.util.List;
import net.minecraft.util.ARGB;

/**
 * Reads a glow beam's recorded vertices back as what a breathing test
 * compares: each vertex's alpha in emission order, and the beam's widest
 * half-width.
 */
public final class BeamSamples {

    private BeamSamples() {
    }

    /**
     * @param vertices the recorded vertices
     * @return each vertex's alpha, in emission order
     */
    public static List<Integer> alphas(List<RecordingVertexConsumer.Vertex> vertices) {
        return vertices.stream().map(v -> ARGB.alpha(v.color())).toList();
    }

    /**
     * @param vertices the recorded vertices
     * @return the outermost layer's half-width, the largest of any vertex's |x| and |z|
     */
    public static float widest(List<RecordingVertexConsumer.Vertex> vertices) {
        float widest = 0f;
        for (RecordingVertexConsumer.Vertex v : vertices) {
            widest = Math.max(widest, Math.max(Math.abs(v.x()), Math.abs(v.z())));
        }
        return widest;
    }
}
