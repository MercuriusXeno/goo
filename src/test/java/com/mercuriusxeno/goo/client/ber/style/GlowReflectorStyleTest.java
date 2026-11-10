package com.mercuriusxeno.goo.client.ber.style;

import com.mercuriusxeno.goo.client.BeamSamples;
import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mercuriusxeno.goo.client.ability.GlowBeamMesh;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * GlowReflectorStyle: one end of each link draws its beam, the beam draws
 * brighter the more light the network carries, and each of its layers fades
 * out past both prisms' middles rather than stopping square, breathing in
 * alpha on top of the light share while its width holds.
 * decision reflector-rails-carry-the-brightest-light
 * decision reflector-rail-blooms-out-inside-the-prism
 * decision every-glow-beam-breathes-in-brightness
 */
class GlowReflectorStyleTest {

    private static final int RAIL_LIGHT = 10;
    private static final float RAIL_LENGTH = 4f;
    private static final float TOLERANCE = 1e-4f;

    @Test
    void exactlyOneEndDrawsEachBeam() {
        Vec3[] links = {new Vec3(3, 0, 0), new Vec3(0, -2, 0), new Vec3(0, 0, 7), new Vec3(-5, 4, 5)};
        for (Vec3 link : links) {
            assertNotEquals(GlowReflectorStyle.drawsTheBeam(link), GlowReflectorStyle.drawsTheBeam(link.reverse()),
                    link.toString());
        }
    }

    private static List<RecordingVertexConsumer.Vertex> emitRail(float share, float breath) {
        RecordingVertexConsumer consumer = new RecordingVertexConsumer();
        GlowReflectorStyle.emitBeam(new PoseStack().last(), consumer, RAIL_LENGTH, share, 0f, breath);
        return consumer.vertices();
    }

    @Test
    void theRailBreathesInAlphaOnTopOfTheLightShareWhileItsWidthHolds() {
        float share = GlowReflectorStyle.lightShare(RAIL_LIGHT);
        List<RecordingVertexConsumer.Vertex> peak = emitRail(share, GlowBeamMesh.breath(0));
        List<RecordingVertexConsumer.Vertex> trough =
                emitRail(share, GlowBeamMesh.breath(GlowBeamMesh.BREATH_PERIOD_TICKS / 2));

        assertNotEquals(BeamSamples.alphas(peak), BeamSamples.alphas(trough), "the rail's alpha breathes");
        assertEquals(BeamSamples.extents(peak), BeamSamples.extents(trough), "the rail's width holds");
        int coreAtPeak = Math.round(ARGB.alpha(GlowBeamMesh.LAYERS.getFirst().color()) * share);
        assertEquals(coreAtPeak, peak.stream().mapToInt(v -> ARGB.alpha(v.color())).max().orElseThrow(),
                "the peak's alpha is the layer's alpha times the share");
    }

    @Test
    void eachRailLayerFadesOutPastBothPrismMiddles() {
        float share = GlowReflectorStyle.lightShare(RAIL_LIGHT);
        List<RecordingVertexConsumer.Vertex> vertices = emitRail(share, 1f);

        float width = GlowReflectorStyle.DIMMEST_WIDTH + (1f - GlowReflectorStyle.DIMMEST_WIDTH) * share;
        for (GlowBeamMesh.Layer layer : GlowBeamMesh.LAYERS) {
            float radius = layer.radius() * width;
            List<RecordingVertexConsumer.Vertex> ofLayer = vertices.stream()
                    .filter(v -> Math.abs(Math.max(Math.abs(v.x()), Math.abs(v.z())) - radius) < TOLERANCE).toList();
            float lowest = (float) ofLayer.stream().mapToDouble(RecordingVertexConsumer.Vertex::y).min().orElseThrow();
            float highest = (float) ofLayer.stream().mapToDouble(RecordingVertexConsumer.Vertex::y).max().orElseThrow();
            assertEquals(-layer.depth(), lowest, TOLERANCE, "the rail fades back past the drawing prism's middle");
            assertEquals(RAIL_LENGTH + layer.depth(), highest, TOLERANCE,
                    "the rail fades on past the linked prism's middle");
            int sharedAlpha = Math.round(ARGB.alpha(layer.color()) * share);
            for (RecordingVertexConsumer.Vertex v : ofLayer) {
                if (Math.abs(v.y() - lowest) < TOLERANCE || Math.abs(v.y() - highest) < TOLERANCE) {
                    assertEquals(0, ARGB.alpha(v.color()), "each end of the rail fades to nothing");
                } else if (v.y() >= -TOLERANCE && v.y() <= RAIL_LENGTH + TOLERANCE) {
                    assertEquals(sharedAlpha, ARGB.alpha(v.color()), "the rail between the middles keeps its alpha");
                }
            }
        }
    }

    @Test
    void theBeamBrightensWithTheNetworksLight() {
        assertEquals(1f, GlowReflectorStyle.lightShare(15), 0f);
        assertEquals(1f / 15, GlowReflectorStyle.lightShare(1), 1e-6f);
        assertEquals(0f, GlowReflectorStyle.lightShare(0), 0f);
    }
}
