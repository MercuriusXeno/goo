package com.mercuriusxeno.goo.client.ber.style;

import com.mercuriusxeno.goo.client.BeamSamples;
import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mercuriusxeno.goo.client.ability.GlowBeamMesh;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GlowBeaconStyle: the bulb beacon's layers breathe in alpha and shrink
 * slightly at the ease, never past their own width.
 * decision every-glow-beam-breathes-in-brightness
 */
class GlowBeaconStyleTest {

    private static final float RADIUS_SCALE = 1.5f;
    private static final float TOLERANCE = 1e-4f;

    private static List<RecordingVertexConsumer.Vertex> emitBeacon(float breath) {
        RecordingVertexConsumer consumer = new RecordingVertexConsumer();
        for (GlowBeamMesh.Layer layer : GlowBeamMesh.LAYERS) {
            GlowBeaconStyle.emitBeaconLayer(new PoseStack().last(), consumer, layer, RADIUS_SCALE, 0f, breath);
        }
        return consumer.vertices();
    }

    @Test
    void theBeaconBreathesInAlphaAndShrinksSlightlyAtTheEase() {
        List<RecordingVertexConsumer.Vertex> peak = emitBeacon(GlowBeamMesh.breath(0));
        List<RecordingVertexConsumer.Vertex> trough = emitBeacon(GlowBeamMesh.breath(GlowBeamMesh.BREATH_PERIOD_TICKS / 2));

        assertNotEquals(BeamSamples.alphas(peak), BeamSamples.alphas(trough), "the beacon's alpha breathes");
        assertEquals(GlowBeamMesh.LAYERS.getLast().radius() * RADIUS_SCALE, BeamSamples.widest(peak), TOLERANCE,
                "the beacon is never fatter than its own width");
        assertTrue(BeamSamples.widest(trough) < BeamSamples.widest(peak), "the beacon shrinks slightly at the ease");
    }
}
