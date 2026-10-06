package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.LineContext;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * Draws a small reticule facing the camera at the aimed point, the mark a
 * free or channeled ability shows where it will land.
 * target-kind-configured-per-ability
 */
final class ReticuleRenderer {
    /** The reticule's ring radius per block of distance, so it holds its size on screen. */
    static final double RADIUS_PER_BLOCK = 0.025;
    /** The smallest ring radius in blocks, for a point close to the eye. */
    static final double MIN_RADIUS = 0.06;
    /** Line segments the ring is drawn with. */
    static final int RING_SEGMENTS = 24;
    /** How far each tick reaches inward from the ring, as a share of the radius. */
    static final double TICK_INNER_SHARE = 0.4;
    /** How far each tick reaches past the ring, as a share of the radius. */
    static final double TICK_OUTER_SHARE = 1.5;
    /** How far the reticule stands off the point toward the camera, as a share of the radius. */
    static final double STAND_OFF_SHARE = 3.0;
    /** The most the reticule stands off, as a share of the distance to the camera. */
    static final double MAX_STAND_OFF_SHARE = 0.5;
    /** Alpha of the reticule's lines on the additive glow lines. */
    private static final int ALPHA = 200;
    /** Below this, the view runs along the world's vertical axis. */
    private static final double VERTICAL_EPSILON = 1e-6;

    private ReticuleRenderer() {}

    /**
     * The ring radius for a point at a distance from the eye.
     *
     * @param distance blocks from the camera to the point
     * @return the radius in blocks
     */
    static double radiusAt(double distance) {
        return Math.max(MIN_RADIUS, distance * RADIUS_PER_BLOCK);
    }

    /**
     * Where the reticule draws: the aimed point pulled toward the camera, so a
     * ring on a block face stands in front of the face rather than in its
     * plane, where the face's depth hides it.
     *
     * @param point  the aimed point
     * @param camera the camera position
     * @param radius the ring radius in blocks
     * @return the drawn center, on the line from the point to the camera
     */
    static Vec3 standOffPoint(Vec3 point, Vec3 camera, double radius) {
        Vec3 toCamera = camera.subtract(point);
        double distance = toCamera.length();
        double standOff = Math.min(radius * STAND_OFF_SHARE, distance * MAX_STAND_OFF_SHARE);
        return distance == 0 ? point : point.add(toCamera.scale(standOff / distance));
    }

    /**
     * The reticule's polylines: a ring around the point in the plane facing the
     * camera, then four ticks crossing it up, down, left and right.
     *
     * @param point  the aimed point
     * @param camera the camera position
     * @param radius the ring radius in blocks
     * @return the polylines, the ring first
     */
    static List<Vec3[]> reticuleLines(Vec3 point, Vec3 camera, double radius) {
        Vec3 forward = point.subtract(camera).normalize();
        Vec3 right = forward.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < VERTICAL_EPSILON) {
            right = new Vec3(1, 0, 0);
        }
        right = right.normalize();
        Vec3 up = right.cross(forward).normalize();
        List<Vec3[]> lines = new ArrayList<>();
        Vec3[] ring = new Vec3[RING_SEGMENTS + 1];
        for (int i = 0; i <= RING_SEGMENTS; i++) {
            double angle = Math.TAU * i / RING_SEGMENTS;
            ring[i] = point.add(right.scale(radius * Math.cos(angle))).add(up.scale(radius * Math.sin(angle)));
        }
        lines.add(ring);
        for (Vec3 axis : List.of(up, up.reverse(), right, right.reverse())) {
            lines.add(new Vec3[] {point.add(axis.scale(radius * TICK_INNER_SHARE)),
                point.add(axis.scale(radius * TICK_OUTER_SHARE))});
        }
        return lines;
    }

    /**
     * Draws the reticule at the point in the goo's color.
     *
     * @param poseStack    the pose stack
     * @param bufferSource the buffer source
     * @param camera       the render camera
     * @param point        the aimed point
     * @param rgb          the goo's highlight color
     */
    static void render(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
            Camera camera, Vec3 point, int rgb) {
        Vec3 cam = camera.position();
        float width = Minecraft.getInstance().getWindow().getAppropriateLineWidth();
        LineContext ctx = new LineContext(poseStack.last(), bufferSource.getBuffer(GooRenderTypes.LINES_GLOW));
        int color = ARGB.color(ALPHA, ARGB.red(rgb), ARGB.green(rgb), ARGB.blue(rgb));
        double radius = radiusAt(point.distanceTo(cam));
        for (Vec3[] line : reticuleLines(standOffPoint(point, cam, radius), cam, radius)) {
            ctx.emitPolyline(cam, line, color, width);
        }
        bufferSource.endLastBatch();
    }
}
