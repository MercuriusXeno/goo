package com.mercuriusxeno.goo.client.ber.style;

import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.util.ARGB;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GlowBeaconStyle's beam: each layer is a tube reaching the beacon's full
 * length, and the layers widen and fade from the core out so they bloom.
 * decision bulb-one-model-max-light-beacon-combo
 */
class GlowBeaconStyleTest {

    private static final float RADIUS = 0.25f;
    private static final int COLOR = 0x80FFE628;
    private static final float TOLERANCE = 1e-5f;
    private static final int VERTICES_PER_TUBE = 16;

    @Test
    void layerIsATubeFromTheBaseToTheBeaconsReach() {
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
        assertEquals(GlowBeaconStyle.BEAM_REACH, highest, TOLERANCE);
    }

    @Test
    void layersWidenAndFadeFromTheCoreOut() {
        List<GlowBeaconStyle.Layer> layers = GlowBeaconStyle.LAYERS;
        for (int i = 1; i < layers.size(); i++) {
            GlowBeaconStyle.Layer inner = layers.get(i - 1);
            GlowBeaconStyle.Layer outer = layers.get(i);
            assertTrue(outer.radius() > inner.radius(), "layer " + i + " should be wider than the one inside it");
            assertTrue(ARGB.alpha(outer.color()) < ARGB.alpha(inner.color()),
                    "layer " + i + " should be fainter than the one inside it");
        }
    }

    private static List<RecordingVertexConsumer.Vertex> emit() {
        RecordingVertexConsumer consumer = new RecordingVertexConsumer();
        GlowBeaconStyle.emitLayer(new PoseStack().last(), consumer, RADIUS, COLOR, 0f);
        return consumer.vertices();
    }
}
