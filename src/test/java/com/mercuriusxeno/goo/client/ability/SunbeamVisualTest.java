package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SunbeamVisual's stretch of beam: one ending in a prism runs on past its end
 * and fades to nothing there, one ending anywhere else stops flat at its end.
 * decision reflector-rail-blooms-out-inside-the-prism
 */
class SunbeamVisualTest {

    private static final Vec3 FROM = Vec3.ZERO;
    private static final double LENGTH = 6;
    private static final Vec3 TO = new Vec3(0, LENGTH, 0);
    private static final float TOLERANCE = 1e-4f;

    private static List<RecordingVertexConsumer.Vertex> drawSegment(boolean endsInPrism) {
        RecordingVertexConsumer consumer = new RecordingVertexConsumer();
        SunbeamVisual.drawSegment(new PoseStack(), consumer, FROM, TO, 0f, endsInPrism);
        return consumer.vertices();
    }

    private static float highestY(List<RecordingVertexConsumer.Vertex> vertices) {
        return (float) vertices.stream().mapToDouble(RecordingVertexConsumer.Vertex::y).max().orElseThrow();
    }

    @Test
    void aStretchEndingInAPrismFadesOutPastItsEnd() {
        List<RecordingVertexConsumer.Vertex> vertices = drawSegment(true);
        float highest = highestY(vertices);

        assertTrue(highest > LENGTH + TOLERANCE, "the prism stretch reaches past its end, highest " + highest);
        vertices.stream().filter(v -> Math.abs(v.y() - highest) < TOLERANCE)
                .forEach(v -> assertEquals(0, ARGB.alpha(v.color()), "the far ring fades to nothing"));
    }

    @Test
    void aStretchEndingAnywhereElseStopsFlatAtItsEnd() {
        List<RecordingVertexConsumer.Vertex> vertices = drawSegment(false);
        float highest = highestY(vertices);

        assertEquals(LENGTH, highest, TOLERANCE);
        List<Integer> layerAlphas = GlowBeamMesh.LAYERS.stream().map(layer -> ARGB.alpha(layer.color())).toList();
        vertices.stream().filter(v -> Math.abs(v.y() - highest) < TOLERANCE)
                .forEach(v -> assertTrue(layerAlphas.contains(ARGB.alpha(v.color())),
                        "the flat end keeps its layer's alpha, read " + ARGB.alpha(v.color())));
    }
}
