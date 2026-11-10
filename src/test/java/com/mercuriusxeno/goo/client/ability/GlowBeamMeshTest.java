package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.util.ARGB;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GlowBeamMesh: each layer is a tube reaching the length it is given, the
 * layers widen and fade from the core out so they bloom, and a fading layer
 * runs on past its length to nothing, the core deepest.
 * decisions bulb-one-model-max-light-beacon-combo, sunbeam-splits-at-the-prism-with-a-glisten
 * decision reflector-rail-blooms-out-inside-the-prism
 */
class GlowBeamMeshTest {

    private static final float RADIUS = 0.25f;
    private static final int COLOR = 0x80FFE628;
    private static final float TOLERANCE = 1e-5f;
    private static final int VERTICES_PER_TUBE = 16;
    private static final float LENGTH = 37.5f;

    @Test
    void layerIsATubeFromTheOriginToItsLength() {
        List<RecordingVertexConsumer.Vertex> vertices = emit();

        assertEquals(VERTICES_PER_TUBE, vertices.size());
        float lowest = Float.MAX_VALUE;
        float highest = -Float.MAX_VALUE;
        for (RecordingVertexConsumer.Vertex v : vertices) {
            assertEquals(RADIUS, Math.max(Math.abs(v.x()), Math.abs(v.z())), TOLERANCE);
            assertEquals(COLOR, v.color());
            lowest = Math.min(lowest, v.y());
            highest = Math.max(highest, v.y());
        }
        assertEquals(0f, lowest, TOLERANCE);
        assertEquals(LENGTH, highest, TOLERANCE);
    }

    @Test
    void layersWidenAndFadeFromTheCoreOut() {
        List<GlowBeamMesh.Layer> layers = GlowBeamMesh.LAYERS;
        for (int i = 1; i < layers.size(); i++) {
            GlowBeamMesh.Layer inner = layers.get(i - 1);
            GlowBeamMesh.Layer outer = layers.get(i);
            assertTrue(outer.radius() > inner.radius(), "layer " + i + " should be wider than the one inside it");
            assertTrue(ARGB.alpha(outer.color()) < ARGB.alpha(inner.color()),
                    "layer " + i + " should be fainter than the one inside it");
        }
    }

    @Test
    void fadingLayerReachesPastItsLengthByItsDepthAndFadesToNothing() {
        float depth = 0.4f;
        RecordingVertexConsumer consumer = new RecordingVertexConsumer();
        GlowBeamMesh.emitFadingLayer(new PoseStack().last(), consumer, RADIUS, COLOR, 0f, LENGTH, depth);
        List<RecordingVertexConsumer.Vertex> vertices = consumer.vertices();

        float highest = (float) vertices.stream().mapToDouble(RecordingVertexConsumer.Vertex::y).max().orElseThrow();
        assertEquals(LENGTH + depth, highest, TOLERANCE);
        for (RecordingVertexConsumer.Vertex v : vertices) {
            if (Math.abs(v.y() - highest) < TOLERANCE) {
                assertEquals(0, ARGB.alpha(v.color()), "the far ring fades to nothing");
            } else if (v.y() <= LENGTH + TOLERANCE) {
                assertEquals(ARGB.alpha(COLOR), ARGB.alpha(v.color()), "the tube up to its length keeps its alpha");
            }
        }
    }

    @Test
    void fadeDepthsShrinkFromTheCoreOutWithinOneBlock() {
        List<GlowBeamMesh.Layer> layers = GlowBeamMesh.LAYERS;
        assertTrue(layers.getFirst().depth() <= 1f, "the core's fade stays inside one block");
        for (int i = 1; i < layers.size(); i++) {
            assertTrue(layers.get(i).depth() < layers.get(i - 1).depth(),
                    "layer " + i + " should fade out shallower than the one inside it");
        }
    }

    private static List<RecordingVertexConsumer.Vertex> emit() {
        RecordingVertexConsumer consumer = new RecordingVertexConsumer();
        GlowBeamMesh.emitLayer(new PoseStack().last(), consumer, RADIUS, COLOR, 0f, LENGTH);
        return consumer.vertices();
    }
}
