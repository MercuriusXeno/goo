package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.util.ARGB;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GlowBeamMesh: each layer is a tube reaching the length it is given, the
 * layers widen and fade from the core out so they bloom, and a fading layer
 * runs on past its length to nothing, the core deepest; the beam's breath
 * swells and eases in a slight band below one; every face runs the soft beam
 * texture, transparent at both sides, across its whole width.
 * decisions bulb-one-model-max-light-beacon-combo, sunbeam-splits-at-the-prism-with-a-glisten
 * decision reflector-rail-blooms-out-inside-the-prism
 * decision every-glow-beam-breathes-in-brightness
 * decision every-glow-beam-feathers-to-transparent
 */
class GlowBeamMeshTest {

    private static final float RADIUS = 0.25f;
    private static final int COLOR = 0x80FFE628;
    private static final float TOLERANCE = 1e-5f;
    private static final int VERTICES_PER_STRIP = 4;
    private static final float LENGTH = 37.5f;
    private static final int VERTICES_PER_FACE = 4;
    /** The camera sits corner-on to a square tube: this far off the beam on x and on z. */
    private static final float CAMERA_ACROSS = 3f;
    private static final float CAMERA_ALONG = 2f;
    private static final int OPAQUE = 255;
    private static final int BREATH_SAMPLES = 64;
    private static final double START_TICKS = 123_456.25;
    /** A breath dims a beam by a fifth of its alpha at most. */
    private static final float MOST_A_BREATH_DIMS = 0.8f;

    @Test
    void layerIsAStripFromTheOriginToItsLength() {
        List<RecordingVertexConsumer.Vertex> vertices = emit();

        assertEquals(VERTICES_PER_STRIP, vertices.size());
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
    void eachLayerIsAFlatStripTurnedToTheCameraSoItsMiddleNeverSeesThrough() {
        PoseStack cameraRelative = new PoseStack();
        cameraRelative.translate(-CAMERA_ACROSS, -CAMERA_ALONG, -CAMERA_ACROSS);
        RecordingVertexConsumer consumer = new RecordingVertexConsumer();
        GlowBeamMesh.emitLayer(cameraRelative.last(), consumer, RADIUS, COLOR, 0f, LENGTH);

        for (RecordingVertexConsumer.Vertex v : consumer.vertices()) {
            float offsetX = v.x() + CAMERA_ACROSS;
            float offsetZ = v.z() + CAMERA_ACROSS;
            assertEquals(0f, offsetX * CAMERA_ACROSS + offsetZ * CAMERA_ACROSS, TOLERANCE,
                    "a vertex at " + offsetX + "," + offsetZ + " leaves the strip turned to the camera");
            assertEquals(RADIUS, (float) Math.hypot(offsetX, offsetZ), TOLERANCE, "the strip spans the layer's width");
        }
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

    @Test
    void breathSwellsAndEasesInItsSlightBandAndReturnsAfterAPeriod() {
        float period = GlowBeamMesh.BREATH_PERIOD_TICKS;
        float highest = -Float.MAX_VALUE;
        float lowest = Float.MAX_VALUE;
        for (int sample = 0; sample <= BREATH_SAMPLES; sample++) {
            double ticks = START_TICKS + period * sample / BREATH_SAMPLES;
            float breath = GlowBeamMesh.breath(ticks);
            assertTrue(breath <= 1f, "breath at " + ticks + " rose above one: " + breath);
            assertTrue(breath >= GlowBeamMesh.BREATH_FLOOR, "breath at " + ticks + " fell below its floor: " + breath);
            assertEquals(breath, GlowBeamMesh.breath(ticks + period), TOLERANCE, "breath returns a period later");
            highest = Math.max(highest, breath);
            lowest = Math.min(lowest, breath);
        }
        assertTrue(highest > lowest, "breath swells and eases across a period");
        assertTrue(GlowBeamMesh.BREATH_FLOOR >= MOST_A_BREATH_DIMS, "the band stays slight");
    }

    @Test
    void theSoftBeamTextureFeathersToNothingAtBothSides() throws IOException {
        String path = "/assets/" + GlowBeamMesh.TEXTURE.getNamespace() + "/" + GlowBeamMesh.TEXTURE.getPath();
        BufferedImage texture;
        try (InputStream in = GlowBeamMeshTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing on the classpath: " + path);
            texture = ImageIO.read(in);
        }
        int last = texture.getWidth() - 1;
        int center = texture.getWidth() / 2;
        for (int y = 0; y < texture.getHeight(); y++) {
            assertEquals(0, texture.getRGB(0, y) >>> 24, "row " + y + " is transparent at its first column");
            assertEquals(0, texture.getRGB(last, y) >>> 24, "row " + y + " is transparent at its last column");
            assertEquals(OPAQUE, texture.getRGB(center, y) >>> 24, "row " + y + " is opaque at its center column");
        }
    }

    @Test
    void eachFaceRunsTheTextureAcrossItsWholeWidth() {
        List<RecordingVertexConsumer.Vertex> vertices = emit();
        for (int face = 0; face < vertices.size() / VERTICES_PER_FACE; face++) {
            List<RecordingVertexConsumer.Vertex> quad =
                    vertices.subList(face * VERTICES_PER_FACE, (face + 1) * VERTICES_PER_FACE);
            float[] us = {quad.get(0).u(), quad.get(1).u(), quad.get(2).u(), quad.get(3).u()};
            assertEquals(0f, Math.min(Math.min(us[0], us[1]), Math.min(us[2], us[3])), TOLERANCE,
                    "face " + face + " starts the texture's width at one side edge");
            assertEquals(1f, Math.max(Math.max(us[0], us[1]), Math.max(us[2], us[3])), TOLERANCE,
                    "face " + face + " ends the texture's width at the other");
            for (RecordingVertexConsumer.Vertex v : quad) {
                RecordingVertexConsumer.Vertex across = quad.stream().filter(w -> w.y() == v.y() && w != v)
                        .findFirst().orElseThrow();
                assertEquals(1f, Math.abs(v.u() - across.u()), TOLERANCE,
                        "face " + face + " spans u 0 to 1 between its side edges");
            }
        }
    }

    private static List<RecordingVertexConsumer.Vertex> emit() {
        RecordingVertexConsumer consumer = new RecordingVertexConsumer();
        GlowBeamMesh.emitLayer(new PoseStack().last(), consumer, RADIUS, COLOR, 0f, LENGTH);
        return consumer.vertices();
    }
}
