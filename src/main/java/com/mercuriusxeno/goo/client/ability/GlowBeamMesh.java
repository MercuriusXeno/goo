package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.List;

/**
 * Glow's beam of light, shared by Bulb's prism beacon, Reflector's rails and Sunbeam's ray: a
 * thin near-white core inside wider, fainter glow-yellow shells, each a flat
 * strip turned to face the camera and feathered at its sides, blended
 * additively so the layers stack into a bloom and stay see-through.
 * decisions bulb-one-model-max-light-beacon-combo, sunbeam-splits-at-the-prism-with-a-glisten
 */
public final class GlowBeamMesh {

    /**
     * The beam's layers from the core out, each wider and fainter than the
     * last; slimmed and dimmed after UAT found them too bright and too thick
     * (operator ruling 2026-10-09), then made smaller with the layers drawn
     * tighter together (operator ruling 2026-11-08), then widened by half
     * again once each layer became one feathered strip and read too skinny
     * beside the tube it replaced (operator UAT 2026-11-10). Each fades out past a
     * prism-bound end by a depth, the core deepest, every depth under half a
     * block so the bloom stays inside the prism's block whether the beam stops
     * on its face or runs into its middle, as a reflector rail does.
     * decision reflector-rail-blooms-out-inside-the-prism
     */
    public static final List<Layer> LAYERS = List.of(
            new Layer(0.0525f, ARGB.color(120, 0xFFF6C8), 0.45f),
            new Layer(0.09f, ARGB.color(66, 0xFFE628), 0.35f),
            new Layer(0.1425f, ARGB.color(27, 0xFFD700), 0.25f),
            new Layer(0.21f, ARGB.color(11, 0xFFD700), 0.15f));
    /**
     * Glow's soft beam texture, transparent at both side columns and opaque
     * at mid-width, which every face runs across its whole width so the
     * beam's silhouette feathers instead of stair-stepping.
     * decision every-glow-beam-feathers-to-transparent
     */
    public static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Goo.MODID,
            "textures/entity/glow_beam.png");
    /**
     * Ticks one breath of the beam's brightness takes, swell to swell: ten
     * seconds, after UAT asked for a very gentle, delicate wave rather than a
     * pulse (operator ruling 2026-11-10).
     */
    public static final float BREATH_PERIOD_TICKS = 200f;
    /**
     * The share of its alpha a beam keeps at the trough of its breath: a very
     * shallow dip, after UAT asked for a very gentle, delicate wave (operator
     * ruling 2026-11-10).
     */
    public static final float BREATH_FLOOR = 0.96f;
    /** Blocks of beam one tile of the texture spans. */
    private static final float BLOCKS_PER_TILE = 1f;
    private static final double HALF = 0.5;
    /** Below this squared distance off the beam's axis the camera gives the strip no turn. */
    private static final float ON_THE_AXIS_SQUARED = 1e-8f;
    /** One whole turn of the breath's cosine, in radians. */
    private static final double FULL_TURN = 2 * Math.PI;
    private static final Vector3f UP = new Vector3f(0f, 1f, 0f);

    private GlowBeamMesh() {
    }

    /**
     * The share of its alpha a glow beam keeps at a moment of its breath: one
     * at the swell, {@link #BREATH_FLOOR} at the ease, a smooth cosine
     * between, so the beam reads alive without flicker.
     * decision every-glow-beam-breathes-in-brightness
     *
     * @param ticks the game time in ticks, with the frame's partial tick
     * @return the alpha share, from the floor to one
     */
    public static float breath(double ticks) {
        double eased = (1 - Math.cos(ticks / BREATH_PERIOD_TICKS * FULL_TURN)) * HALF;
        return (float) (1 - (1 - BREATH_FLOOR) * eased);
    }

    /**
     * A layer's color with its alpha scaled by a breath, the hue kept.
     * decision every-glow-beam-breathes-in-brightness
     *
     * @param color  the packed ARGB color
     * @param breath the alpha share {@link #breath} answered
     * @return the breathed color
     */
    public static int breathed(int color, float breath) {
        return ARGB.color(Math.round(ARGB.alpha(color) * breath), color);
    }

    /**
     * Emits one layer: a flat strip turned to the camera along the pose's +y
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
     * decision reflector-rail-blooms-out-inside-the-prism
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

    /**
     * Emits one layer that blooms out at both ends: a tail fading back past
     * its origin by a depth, the tube to its length, and a tail fading on past
     * the length by the same depth, each tail from the layer's alpha to nothing.
     * decision reflector-rail-blooms-out-inside-the-prism
     *
     * @param pose   the pose, its +y along the beam
     * @param buffer the vertex consumer
     * @param radius the tube's half-width in blocks
     * @param color  the layer's packed ARGB color
     * @param scroll the texture's scroll offset in [0, 1)
     * @param length how far the tube reaches at full alpha, in blocks
     * @param depth  how far past each end the tail fades out, in blocks
     */
    public static void emitLayerFadingAtBothEnds(PoseStack.Pose pose, VertexConsumer buffer, float radius,
                                                 int color, float scroll, float length, float depth) {
        emitFaces(pose, buffer, radius, ring(-depth, ARGB.color(0, color), scroll), ring(0f, color, scroll));
        emitFadingLayer(pose, buffer, radius, color, scroll, length, depth);
    }

    private static Ring ring(float y, int color, float scroll) {
        return new Ring(y, color, scroll + y / BLOCKS_PER_TILE);
    }

    /**
     * Emits one flat strip along the pose's +y axis, from a near ring to a far
     * one, turned about the axis to face the camera: its feathered side
     * columns always sit on the beam's outline and its opaque middle down the
     * beam's centre, where a square tube's transparent corners showed a
     * see-through seam whenever a corner faced the camera (operator UAT 2026-11-10).
     * decision every-glow-beam-feathers-to-transparent
     *
     * @param pose   the pose, its +y along the beam, its parent space centred on the camera
     * @param buffer the vertex consumer
     * @param radius the strip's half-width in blocks
     * @param near   the ring the strip starts at
     * @param far    the ring the strip ends at
     */
    private static void emitFaces(PoseStack.Pose pose, VertexConsumer buffer, float radius, Ring near, Ring far) {
        Vector3f side = sideFacingTheCamera(pose).mul(radius);
        vertex(pose, buffer, near.color(), -side.x, near.y(), -side.z, 0f, near.v());
        vertex(pose, buffer, near.color(), side.x, near.y(), side.z, 1f, near.v());
        vertex(pose, buffer, far.color(), side.x, far.y(), side.z, 1f, far.v());
        vertex(pose, buffer, far.color(), -side.x, far.y(), -side.z, 0f, far.v());
    }

    /**
     * The unit direction across the beam that faces the camera: square to
     * the beam's +y axis and to the camera's offset from that axis, with the
     * camera at the origin of the pose's parent space; +x where the camera
     * sits on the axis itself.
     *
     * @param pose the pose, its +y along the beam
     * @return the strip's half-width direction in the pose's space
     */
    private static Vector3f sideFacingTheCamera(PoseStack.Pose pose) {
        Vector3f camera = new Matrix4f(pose.pose()).invert().transformPosition(new Vector3f());
        Vector3f side = new Vector3f(camera.z, 0f, -camera.x);
        return side.lengthSquared() < ON_THE_AXIS_SQUARED ? new Vector3f(1f, 0f, 0f) : side.normalize();
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
