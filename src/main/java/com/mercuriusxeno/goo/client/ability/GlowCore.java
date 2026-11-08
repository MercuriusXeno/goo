package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.ber.WispRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.ARGB;
import java.util.List;

/**
 * The core of light a glow beam emanates from inside its prism: nested cubes
 * added onto the world, a near-white middle brighter and wider than the
 * beam's own core, each shell out wider and fainter and every one larger than
 * the beam, so the light gathers where the beam starts rather than ending in
 * the tube's square cut (operator ruling 2026-11-08: a cuboid, larger than
 * the beam).
 * decision diagnose-and-correct-the-sunbeam-prism-depth
 */
public final class GlowCore {

    /** The core's layers from the middle out, each a cube's half-size, wider and fainter than the one inside it. */
    public static final List<GlowBeamMesh.Layer> LAYERS = List.of(
            new GlowBeamMesh.Layer(0.08f, ARGB.color(200, 0xFFFFF0)),
            new GlowBeamMesh.Layer(0.11f, ARGB.color(120, 0xFFF6C8)),
            new GlowBeamMesh.Layer(0.15f, ARGB.color(60, 0xFFE628)),
            new GlowBeamMesh.Layer(0.20f, ARGB.color(28, 0xFFD700)),
            new GlowBeamMesh.Layer(0.26f, ARGB.color(12, 0xFFD700)));

    private GlowCore() {
    }

    /**
     * Emits the core centered on the pose's origin.
     *
     * @param pose   the pose
     * @param buffer the vertex consumer, position and color
     * @param scale  the factor every layer's half-size takes
     * @param share  the share of every layer's alpha it keeps, 0 to 1
     */
    public static void emit(PoseStack.Pose pose, VertexConsumer buffer, float scale, float share) {
        for (GlowBeamMesh.Layer layer : LAYERS) {
            int color = ARGB.color(Math.round(ARGB.alpha(layer.color()) * share), layer.color());
            WispRenderer.emitCube(pose, buffer, layer.radius() * scale, color);
        }
    }
}
