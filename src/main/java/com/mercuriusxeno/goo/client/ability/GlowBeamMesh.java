package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.ARGB;
import java.util.List;

/**
 * Glow's beam of light, shared by Bulb's prism beacon and Sunbeam's ray: a
 * thin near-white core inside wider, fainter glow-yellow shells, each a
 * square tube blended additively so the layers stack into a bloom and stay
 * see-through.
 * decisions bulb-one-model-max-light-beacon-combo, sunbeam-splits-at-the-prism-with-a-glisten
 */
public final class GlowBeamMesh {

    /** The beam's layers from the core out, each wider and fainter than the last. */
    public static final List<Layer> LAYERS = List.of(
            new Layer(0.06f, ARGB.color(200, 0xFFF6C8)),
            new Layer(0.13f, ARGB.color(110, 0xFFE628)),
            new Layer(0.24f, ARGB.color(45, 0xFFD700)),
            new Layer(0.40f, ARGB.color(18, 0xFFD700)));
    /** Blocks of beam one tile of the texture spans. */
    private static final float BLOCKS_PER_TILE = 1f;

    private GlowBeamMesh() {
    }

    /**
     * Emits one layer: a square tube of four faces around the pose's +y
     * axis, from its origin out to a length.
     *
     * @param pose   the pose, its +y along the beam
     * @param buffer the vertex consumer
     * @param radius the tube's half-width in blocks
     * @param color  the layer's packed ARGB color
     * @param scroll the texture's scroll offset in [0, 1)
     * @param length how far the tube reaches, in blocks
     */
    public static void emitLayer(PoseStack.Pose pose, VertexConsumer buffer, float radius, int color, float scroll,
                                 float length) {
        float vTop = scroll + length / BLOCKS_PER_TILE;
        float[][] corners = {{-radius, -radius}, {radius, -radius}, {radius, radius}, {-radius, radius}};
        for (int side = 0; side < corners.length; side++) {
            float[] from = corners[side];
            float[] to = corners[(side + 1) % corners.length];
            vertex(pose, buffer, color, from[0], 0, from[1], 0f, scroll);
            vertex(pose, buffer, color, to[0], 0, to[1], 1f, scroll);
            vertex(pose, buffer, color, to[0], length, to[1], 1f, vTop);
            vertex(pose, buffer, color, from[0], length, from[1], 0f, vTop);
        }
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, int color, float x, float y, float z,
                               float u, float v) {
        buffer.addVertex(pose, x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(GooSubmitter.fullbrightLight()).setNormal(pose, 0f, 1f, 0f);
    }

    /**
     * One layer of the beam.
     *
     * @param radius the layer's half-width in blocks before any widening
     * @param color  the layer's packed ARGB color, its alpha how strongly it adds
     */
    public record Layer(float radius, int color) {
    }
}
