package com.mercuriusxeno.goo.client;

import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import net.minecraft.util.ARGB;

/**
 * Reads a glow beam's recorded vertices back as what a breathing test
 * compares: each vertex's alpha in emission order, and each layer's
 * half-width, the largest of its |x| and |z|.
 */
public final class BeamSamples {

    /** Half-widths closer than this read as the same layer. */
    private static final double EXTENT_STEP = 1e-4;

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
     * @return every layer's half-width, in steps of {@link #EXTENT_STEP}, smallest first
     */
    public static SortedSet<Long> extents(List<RecordingVertexConsumer.Vertex> vertices) {
        SortedSet<Long> extents = new TreeSet<>();
        for (RecordingVertexConsumer.Vertex v : vertices) {
            extents.add(Math.round(Math.max(Math.abs(v.x()), Math.abs(v.z())) / EXTENT_STEP));
        }
        return extents;
    }
}
