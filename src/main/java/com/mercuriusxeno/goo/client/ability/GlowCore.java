package com.mercuriusxeno.goo.client.ability;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import java.util.List;

/**
 * The core of light a glow beam emanates from inside its prism: nested
 * spheres added onto the world, a near-white middle brighter and wider than
 * the beam's own core, each shell out wider and fainter, so the light
 * gathers softly where the beam starts rather than ending in the tube's
 * square cut.
 * decision diagnose-and-correct-the-sunbeam-prism-depth
 */
public final class GlowCore {

    /** The core's layers from the middle out, each wider and fainter than the one inside it. */
    public static final List<GlowBeamMesh.Layer> LAYERS = List.of(
            new GlowBeamMesh.Layer(0.07f, ARGB.color(190, 0xFFFFF0)),
            new GlowBeamMesh.Layer(0.12f, ARGB.color(110, 0xFFF6C8)),
            new GlowBeamMesh.Layer(0.19f, ARGB.color(60, 0xFFE628)),
            new GlowBeamMesh.Layer(0.28f, ARGB.color(30, 0xFFD700)),
            new GlowBeamMesh.Layer(0.40f, ARGB.color(14, 0xFFD700)),
            new GlowBeamMesh.Layer(0.55f, ARGB.color(6, 0xFFD700)));
    /** Rings from pole to pole each sphere is cut into. */
    private static final int STACKS = 8;
    /** Points around each ring. */
    private static final int SLICES = 12;

    private GlowCore() {
    }

    /**
     * Emits the core centered on the pose's origin.
     *
     * @param pose   the pose
     * @param buffer the vertex consumer, position and color
     * @param scale  the factor every layer's radius takes
     * @param share  the share of every layer's alpha it keeps, 0 to 1
     */
    public static void emit(PoseStack.Pose pose, VertexConsumer buffer, float scale, float share) {
        for (GlowBeamMesh.Layer layer : LAYERS) {
            int color = ARGB.color(Math.round(ARGB.alpha(layer.color()) * share), layer.color());
            emitSphere(pose, buffer, layer.radius() * scale, color);
        }
    }

    private static void emitSphere(PoseStack.Pose pose, VertexConsumer buffer, float radius, int color) {
        for (int stack = 0; stack < STACKS; stack++) {
            float lower = Mth.PI * stack / STACKS;
            float upper = Mth.PI * (stack + 1) / STACKS;
            for (int slice = 0; slice < SLICES; slice++) {
                float from = Mth.TWO_PI * slice / SLICES;
                float to = Mth.TWO_PI * (slice + 1) / SLICES;
                vertex(pose, buffer, radius, lower, from, color);
                vertex(pose, buffer, radius, lower, to, color);
                vertex(pose, buffer, radius, upper, to, color);
                vertex(pose, buffer, radius, upper, from, color);
            }
        }
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float radius, float polar, float around,
                               int color) {
        float ring = Mth.sin(polar) * radius;
        buffer.addVertex(pose, ring * Mth.cos(around), Mth.cos(polar) * radius, ring * Mth.sin(around))
                .setColor(color);
    }
}
