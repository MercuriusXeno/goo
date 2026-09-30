package com.mercuriusxeno.goo.client.ability;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.Direction;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;

/**
 * The goo ring particle's disc (decision goo-swirl-ring-particle): a flat
 * disc square to the face's axis about the particle's position, drawn
 * through {@code goo_ring.vsh / .fsh}. Each vertex carries the theme color
 * in the vertex color's red, green and blue, the particle's progress in its
 * alpha, and its disc-local position in UV0, since a core pipeline takes no
 * per-draw uniforms.
 */
public final class GooRingMesh {

    /** How many wedges run around the disc. */
    static final int SEGMENTS = 48;
    private static final double TWO_PI = 2 * Math.PI;
    private static final int RGB_MASK = 0xFFFFFF;
    private static final int ALPHA_SHIFT = 24;

    private GooRingMesh() {
    }

    /**
     * The particle's progress through its life, including the frame's partial tick.
     *
     * @param age         the particle's age in ticks
     * @param partialTick the frame's partial tick
     * @param lifetime    the particle's lifetime in ticks
     * @return the progress in [0, 1]
     */
    public static float progress(int age, float partialTick, int lifetime) {
        return Math.clamp((age + partialTick) / lifetime, 0f, 1f);
    }

    /**
     * The color every vertex of the disc carries: the theme color, with the progress as its alpha.
     *
     * @param rgb      the theme color as packed RGB
     * @param progress the particle's progress in [0, 1]
     * @return the packed ARGB color
     */
    public static int vertexColor(int rgb, float progress) {
        return NetherDiscMesh.toByte(progress) << ALPHA_SHIFT | rgb & RGB_MASK;
    }

    /**
     * The disc's rim, one point per wedge edge, about the origin.
     *
     * @param face   the face whose axis the disc lies square to
     * @param radius the disc's radius in blocks
     * @return the rim points, SEGMENTS of them, in angle order
     */
    static List<Vector3f> rim(Direction face, float radius) {
        List<Vector3f> points = new ArrayList<>(SEGMENTS);
        for (int i = 0; i < SEGMENTS; i++) {
            points.add(BurnoutGeometry.discPoint(face, angle(i), radius));
        }
        return points;
    }

    /**
     * Emits the disc as one quad per wedge, the center doubled as the wedge's
     * inner corners, into a position, UV0 and color consumer.
     *
     * @param pose   the pose entry, translated to the particle's position
     * @param c      the vertex consumer
     * @param face   the face whose axis the disc lies square to
     * @param radius the disc's radius in blocks
     * @param color  the packed vertex color from {@link #vertexColor}
     */
    public static void emit(PoseStack.Pose pose, VertexConsumer c, Direction face, float radius, int color) {
        List<Vector3f> rim = rim(face, radius);
        for (int i = 0; i < SEGMENTS; i++) {
            int next = (i + 1) % SEGMENTS;
            center(pose, c, color);
            rimVertex(pose, c, rim.get(i), i, color);
            rimVertex(pose, c, rim.get(next), next, color);
            center(pose, c, color);
        }
    }

    private static void center(PoseStack.Pose pose, VertexConsumer c, int color) {
        c.addVertex(pose, 0f, 0f, 0f).setUv(0f, 0f).setColor(color);
    }

    private static void rimVertex(PoseStack.Pose pose, VertexConsumer c, Vector3f point, int index, int color) {
        double angle = angle(index);
        c.addVertex(pose, point.x(), point.y(), point.z())
                .setUv((float) Math.cos(angle), (float) Math.sin(angle)).setColor(color);
    }

    private static double angle(int index) {
        return TWO_PI * index / SEGMENTS;
    }
}
