package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.List;

/**
 * Glow's beam of light, shared by Bulb's prism beacon, Reflector's rails and Sunbeam's ray: a
 * thin near-white core inside wider, fainter glow-yellow shells, each a
 * square tube blended additively so the layers stack into a bloom and stay
 * see-through.
 * decisions bulb-one-model-max-light-beacon-combo, sunbeam-splits-at-the-prism-with-a-glisten
 */
public final class GlowBeamMesh {

    /**
     * The beam's layers from the core out, each wider and fainter than the
     * last; slimmed and dimmed after UAT found them too bright and too thick
     * (operator ruling 2026-10-09), then made smaller with the layers drawn
     * tighter together (operator ruling 2026-11-08). Each fades out past a
     * prism-bound end by a depth, the core deepest, every depth under half a
     * block so the bloom stays inside the prism's block whether the ray stops
     * on its face or runs into its middle.
     * decision sunbeam-ray-blooms-out-inside-the-prism
     */
    public static final List<Layer> LAYERS = List.of(
            new Layer(0.035f, ARGB.color(120, 0xFFF6C8), 0.45f),
            new Layer(0.06f, ARGB.color(66, 0xFFE628), 0.35f),
            new Layer(0.095f, ARGB.color(27, 0xFFD700), 0.25f),
            new Layer(0.14f, ARGB.color(11, 0xFFD700), 0.15f));
    /** Blocks of beam one tile of the texture spans. */
    private static final float BLOCKS_PER_TILE = 1f;
    private static final Vector3f UP = new Vector3f(0f, 1f, 0f);

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
        emitFaces(pose, buffer, radius, ring(0f, color, scroll), ring(length, color, scroll));
    }

    /**
     * Emits one layer that blooms out at its end: the tube to its length,
     * then a tail on past it by a depth, fading from the layer's alpha at the
     * length to nothing at the tail's far ring.
     * decision sunbeam-ray-blooms-out-inside-the-prism
     *
     * @param pose   the pose, its +y along the beam
     * @param buffer the vertex consumer
     * @param radius the tube's half-width in blocks
     * @param color  the layer's packed ARGB color
     * @param scroll the texture's scroll offset in [0, 1)
     * @param length how far the tube reaches at full alpha, in blocks
     * @param depth  how far past the length the tail fades out, in blocks
     */
    public static void emitFadingLayer(PoseStack.Pose pose, VertexConsumer buffer, float radius, int color,
                                       float scroll, float length, float depth) {
        Ring end = ring(length, color, scroll);
        emitFaces(pose, buffer, radius, ring(0f, color, scroll), end);
        emitFaces(pose, buffer, radius, end, ring(length + depth, ARGB.color(0, color), scroll));
    }

    private static Ring ring(float y, int color, float scroll) {
        return new Ring(y, color, scroll + y / BLOCKS_PER_TILE);
    }

    /**
     * Emits four faces around the pose's +y axis, from a near ring to a far one.
     *
     * @param pose   the pose, its +y along the beam
     * @param buffer the vertex consumer
     * @param radius the faces' half-width in blocks
     * @param near   the ring the faces start at
     * @param far    the ring the faces end at
     */
    private static void emitFaces(PoseStack.Pose pose, VertexConsumer buffer, float radius, Ring near, Ring far) {
        float[][] corners = {{-radius, -radius}, {radius, -radius}, {radius, radius}, {-radius, radius}};
        for (int side = 0; side < corners.length; side++) {
            float[] from = corners[side];
            float[] to = corners[(side + 1) % corners.length];
            vertex(pose, buffer, near.color(), from[0], near.y(), from[1], 0f, near.v());
            vertex(pose, buffer, near.color(), to[0], near.y(), to[1], 1f, near.v());
            vertex(pose, buffer, far.color(), to[0], far.y(), to[1], 1f, far.v());
            vertex(pose, buffer, far.color(), from[0], far.y(), from[1], 0f, far.v());
        }
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, int color, float x, float y, float z,
                               float u, float v) {
        buffer.addVertex(pose, x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(GooSubmitter.fullbrightLight()).setNormal(pose, 0f, 1f, 0f);
    }

    /**
     * The turn that lays the mesh's +y axis along a direction.
     *
     * @param along the direction, any length
     * @return the rotation
     */
    public static Quaternionf alongBeam(Vec3 along) {
        Vector3f direction = new Vector3f((float) along.x, (float) along.y, (float) along.z).normalize();
        return new Quaternionf().rotationTo(UP, direction);
    }

    /**
     * One layer of the beam.
     *
     * @param radius the layer's half-width in blocks before any widening
     * @param color  the layer's packed ARGB color, its alpha how strongly it adds
     * @param depth  how far past a prism-bound end the layer fades out, in blocks
     */
    public record Layer(float radius, int color, float depth) {

        /**
         * A layer that never fades past its end, as a core's cube.
         *
         * @param radius the layer's half-width in blocks before any widening
         * @param color  the layer's packed ARGB color, its alpha how strongly it adds
         */
        public Layer(float radius, int color) {
            this(radius, color, 0f);
        }
    }

    /**
     * One end of a run of faces.
     *
     * @param y     how far along the beam it sits, in blocks
     * @param color the packed ARGB color its vertices carry
     * @param v     the texture's v there
     */
    private record Ring(float y, int color, float v) {
    }
}
