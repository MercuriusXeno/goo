package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.BeamSamples;
import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LaserVisual's stretch of beam: it stops flat at its end, and it breathes
 * in alpha and shrinks slightly at the ease.
 * decision every-glow-beam-breathes-in-brightness
 */
class LaserVisualTest {

    private static final Vec3 FROM = Vec3.ZERO;
    private static final double LENGTH = 6;
    private static final Vec3 TO = new Vec3(0, LENGTH, 0);
    private static final float TOLERANCE = 1e-4f;

    private static List<RecordingVertexConsumer.Vertex> drawSegment(float breath) {
        RecordingVertexConsumer consumer = new RecordingVertexConsumer();
        LaserVisual.drawSegment(new PoseStack(), consumer, FROM, TO, 0f, breath);
        return consumer.vertices();
    }

    @Test
    void theRayBreathesInAlphaAndShrinksSlightlyAtTheEase() {
        List<RecordingVertexConsumer.Vertex> peak = drawSegment(GlowBeamMesh.breath(0));
        List<RecordingVertexConsumer.Vertex> trough =
                drawSegment(GlowBeamMesh.breath(GlowBeamMesh.BREATH_PERIOD_TICKS / 2));

        assertNotEquals(BeamSamples.alphas(peak), BeamSamples.alphas(trough), "the ray's alpha breathes");
        assertEquals(GlowBeamMesh.LAYERS.getLast().radius() * LaserVisual.RAY_WIDTH, BeamSamples.widest(peak),
                TOLERANCE, "the ray is never fatter than its own width");
        assertTrue(BeamSamples.widest(trough) < BeamSamples.widest(peak), "the ray shrinks slightly at the ease");
    }

    private static float highestY(List<RecordingVertexConsumer.Vertex> vertices) {
        return (float) vertices.stream().mapToDouble(RecordingVertexConsumer.Vertex::y).max().orElseThrow();
    }

    @Test
    void theStretchStopsFlatAtItsEnd() {
        List<RecordingVertexConsumer.Vertex> vertices = drawSegment(1f);
        float highest = highestY(vertices);

        assertEquals(LENGTH, highest, TOLERANCE);
        List<Integer> layerAlphas = GlowBeamMesh.LAYERS.stream().map(layer -> ARGB.alpha(layer.color())).toList();
        vertices.stream().filter(v -> Math.abs(v.y() - highest) < TOLERANCE)
                .forEach(v -> assertTrue(layerAlphas.contains(ARGB.alpha(v.color())),
                        "the flat end keeps its layer's alpha, read " + ARGB.alpha(v.color())));
    }
}
