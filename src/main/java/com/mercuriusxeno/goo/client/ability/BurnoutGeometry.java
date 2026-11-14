package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.FlatQuadContext;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * The shapes burnout explosions draw about a marker's block, in block-local
 * coordinates: a sphere about the block center, and an annulus square to the
 * placed face's axis (decision elemental-explosion-per-type).
 */
final class BurnoutGeometry {

    /** Offset to the block center from the block's corner. */
    static final float BLOCK_CENTER = 0.5f;
    private static final double TWO_PI = 2 * Math.PI;
    /** Latitude bands on the unit sphere mesh, poles excluded. */
    private static final int SPHERE_LAT_SEGMENTS = 32;
    /** Longitude segments around the unit sphere mesh. */
    private static final int SPHERE_LON_SEGMENTS = 64;
    /** Vertices per quad, matching {@code VertexFormat.Mode.QUADS}. */
    private static final int VERTICES_PER_QUAD = 4;
    /** Latitude offset subtracted from {@code lat / latSegments} to center phi on zero. */
    private static final double LATITUDE_HALF_OFFSET = 0.5;
    /**
     * The unit sphere mesh, every four entries one quad, each vertex also its
     * outward normal.
     */
    private static final List<Vector3f> SPHERE_MESH = List.copyOf(buildSphereMesh());

    /**
     * Colors one annulus vertex from where it sits on the ring.
     */
    @FunctionalInterface
    interface AnnulusColor {
        /**
         * @param angle the vertex's angle about the face axis, in radians
         * @param outer true on the outer edge, false on the inner
         * @return the packed ARGB color
         */
        int at(double angle, boolean outer);
    }

    /**
     * Colors one sphere vertex from its unit direction out of the center.
     */
    @FunctionalInterface
    interface SphereColor {
        /**
         * @param direction the vertex's unit direction from the sphere's center
         * @return the packed ARGB color
         */
        int at(Vector3f direction);
    }

    private BurnoutGeometry() {
    }

    /**
     * Draws into one render type at the marker's block, camera relative,
     * then flushes that type so the explosion lands this frame.
     *
     * @param frame the frame being drawn
     * @param pos   the marker's block position
     * @param type  the explosion's render type
     * @param draw  emits the explosion's vertices in block-local coordinates
     */
    static void drawAtMarker(BurnoutFrame frame, BlockPos pos, RenderType type,
                             BiConsumer<PoseStack.Pose, VertexConsumer> draw) {
        drawAt(frame, Vec3.atLowerCornerOf(pos), type, draw);
    }

    /**
     * Draws into one render type with block-local coordinates measured from
     * a world corner, camera relative, then flushes that type.
     *
     * @param frame  the frame being drawn
     * @param corner the world point block-local coordinates are measured from
     * @param type   the render type
     * @param draw   emits the vertices in block-local coordinates
     */
    static void drawAt(BurnoutFrame frame, Vec3 corner, RenderType type,
                       BiConsumer<PoseStack.Pose, VertexConsumer> draw) {
        PoseStack poseStack = frame.poseStack();
        poseStack.pushPose();
        poseStack.translate(corner.x - frame.camera().x, corner.y - frame.camera().y,
                corner.z - frame.camera().z);
        draw.accept(poseStack.last(), frame.buffers().getBuffer(type));
        poseStack.popPose();
        frame.buffers().endBatch(type);
    }

    /**
     * Emits the unit sphere scaled to radius about the block center, each
     * vertex's normal the unit direction to it.
     *
     * @param pose   the pose entry
     * @param c      the vertex consumer
     * @param radius the sphere's radius in blocks
     * @param color  the packed ARGB color
     */
    static void emitSphere(PoseStack.Pose pose, VertexConsumer c, float radius, int color) {
        emitSphere(pose, c, radius, direction -> color);
    }

    /**
     * Emits the unit sphere scaled to radius about the block center, each
     * vertex colored from its direction.
     *
     * @param pose   the pose entry
     * @param c      the vertex consumer
     * @param radius the sphere's radius in blocks
     * @param color  colors each vertex from its unit direction
     */
    static void emitSphere(PoseStack.Pose pose, VertexConsumer c, float radius, SphereColor color) {
        // burnouts-are-whole-spheres: every sphere stands about the block center, never lifted to a face
        FlatQuadContext sphere = new FlatQuadContext(pose, c);
        for (Vector3f v : SPHERE_MESH) {
            sphere.vertex(BLOCK_CENTER + v.x() * radius, BLOCK_CENTER + v.y() * radius,
                    BLOCK_CENTER + v.z() * radius, color.at(v), v.x(), v.y(), v.z());
        }
    }

    /**
     * Emits an annulus square to the face's axis, centered on the block
     * center shifted along the face's step by lift, each segment one quad.
     *
     * @param pose     the pose entry
     * @param c        the vertex consumer
     * @param face     the placed face
     * @param lift     the shift from the block center along the face's step, in blocks
     * @param inner    the inner radius in blocks, 0 for a full disc
     * @param outer    the outer radius in blocks
     * @param segments how many quads run around the annulus
     * @param color    colors each vertex from where it sits
     */
    static void emitAnnulus(PoseStack.Pose pose, VertexConsumer c, Direction face, float lift,
                            float inner, float outer, int segments, AnnulusColor color) {
        emitAnnulus(pose, c, face, lift, inner, outer, segments, color,
                new Vector3f(face.getStepX(), face.getStepY(), face.getStepZ()));
    }

    /**
     * Emits an annulus whose vertices all carry a normal given outright, which
     * a shader reading no lighting may take as a value of its own: frost's fog
     * reads its seed there (decision nova-ring-grows-with-the-hold).
     *
     * @param pose     the pose entry
     * @param c        the vertex consumer
     * @param face     the placed face
     * @param lift     the shift from the block center along the face's step, in blocks
     * @param inner    the inner radius in blocks, 0 for a full disc
     * @param outer    the outer radius in blocks
     * @param segments how many quads run around the annulus
     * @param color    colors each vertex from where it sits
     * @param normal   the normal every vertex carries
     */
    static void emitAnnulus(PoseStack.Pose pose, VertexConsumer c, Direction face, float lift,
                            float inner, float outer, int segments, AnnulusColor color, Vector3f normal) {
        FlatQuadContext ring = new FlatQuadContext(pose, c);
        for (int i = 0; i < segments; i++) {
            double a0 = TWO_PI * i / segments;
            double a1 = TWO_PI * (i + 1) / segments;
            annulusVertex(ring, face, lift, a0, inner, color.at(a0, false), normal);
            annulusVertex(ring, face, lift, a0, outer, color.at(a0, true), normal);
            annulusVertex(ring, face, lift, a1, outer, color.at(a1, true), normal);
            annulusVertex(ring, face, lift, a1, inner, color.at(a1, false), normal);
        }
    }

    /**
     * Emits one annulus vertex at an angle and radius in the plane square to the face.
     *
     * @param ring   the quad emitter
     * @param face   the placed face
     * @param lift   the shift from the block center along the face's step
     * @param angle  the angle about the face axis in radians
     * @param radius the distance from the center in blocks
     * @param color  the packed color
     * @param normal the normal the vertex carries
     */
    private static void annulusVertex(FlatQuadContext ring, Direction face, float lift, double angle,
                                      float radius, int color, Vector3f normal) {
        Vector3f point = discPoint(face, angle, radius).add(BLOCK_CENTER + face.getStepX() * lift,
                BLOCK_CENTER + face.getStepY() * lift, BLOCK_CENTER + face.getStepZ() * lift);
        ring.vertex(point.x(), point.y(), point.z(), color, normal.x(), normal.y(), normal.z());
    }

    /**
     * A point on a disc square to the face's axis, about the origin.
     *
     * @param face   the face whose axis the disc lies square to
     * @param angle  the angle about the face axis in radians
     * @param radius the distance from the center in blocks
     * @return the point
     */
    static Vector3f discPoint(Direction face, double angle, float radius) {
        float u = (float) Math.cos(angle) * radius;
        float v = (float) Math.sin(angle) * radius;
        return switch (face.getAxis()) {
            case X -> new Vector3f(0f, u, v);
            case Y -> new Vector3f(u, 0f, v);
            case Z -> new Vector3f(u, v, 0f);
        };
    }

    /**
     * A strength that holds whole until start, then falls linearly to
     * nothing at the explosion's end.
     *
     * @param progress the explosion's progress in [0, 1]
     * @param start    the progress at which the fall begins, below 1
     * @return the strength in [0, 1]
     */
    static float fadeAfter(float progress, float start) {
        if (progress <= start) {
            return 1f;
        }
        return Math.max(0f, 1f - (progress - start) / (1f - start));
    }

    /**
     * Cubic ease-out: fast, then slow.
     *
     * @param t progress in [0, 1]
     * @return eased progress in [0, 1]
     */
    static float easeOutCubic(float t) {
        float inverse = 1f - t;
        return 1f - inverse * inverse * inverse;
    }

    /**
     * Builds a UV sphere mesh as a list of unit vectors, every four
     * consecutive entries one quad.
     *
     * @return the unit sphere vertex list
     */
    private static List<Vector3f> buildSphereMesh() {
        List<Vector3f> out = new ArrayList<>(SPHERE_LAT_SEGMENTS * SPHERE_LON_SEGMENTS * VERTICES_PER_QUAD);
        for (int lat = 0; lat < SPHERE_LAT_SEGMENTS; lat++) {
            double phi0 = Math.PI * ((double) lat / SPHERE_LAT_SEGMENTS - LATITUDE_HALF_OFFSET);
            double phi1 = Math.PI * ((double) (lat + 1) / SPHERE_LAT_SEGMENTS - LATITUDE_HALF_OFFSET);
            for (int lon = 0; lon < SPHERE_LON_SEGMENTS; lon++) {
                double theta0 = TWO_PI * lon / SPHERE_LON_SEGMENTS;
                double theta1 = TWO_PI * (lon + 1) / SPHERE_LON_SEGMENTS;
                out.add(sphereVertex(phi0, theta0));
                out.add(sphereVertex(phi1, theta0));
                out.add(sphereVertex(phi1, theta1));
                out.add(sphereVertex(phi0, theta1));
            }
        }
        return out;
    }

    /**
     * Builds one unit-sphere vertex at spherical coordinates (phi, theta).
     *
     * @param phi   latitude in radians, {@code [-PI/2, PI/2]}
     * @param theta longitude in radians, {@code [0, 2*PI]}
     * @return the unit-sphere vertex
     */
    private static Vector3f sphereVertex(double phi, double theta) {
        double cosPhi = Math.cos(phi);
        return new Vector3f((float) (cosPhi * Math.cos(theta)), (float) Math.sin(phi),
                (float) (cosPhi * Math.sin(theta)));
    }
}
