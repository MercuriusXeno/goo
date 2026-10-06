package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.ability.AbilityArea;
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
 * Draws the selected ability's area while right click is held, translucent
 * in the goo type's color: a sphere around the aim point, a line from the
 * hand to it, or a cone from the hand toward it. An ability with no area
 * draws nothing here and shows its reticule alone.
 * right-click-held-previews-release-throws
 */
final class AreaOverlayRenderer {
    /** Line segments each circle is drawn with. */
    static final int CIRCLE_SEGMENTS = 32;
    /** Lines from a cone's apex to its base rim. */
    static final int CONE_EDGES = 4;
    /** Alpha of the area's lines on the additive glow lines, translucent. */
    private static final int ALPHA = 120;
    /** Below this, a direction runs along the world's vertical axis. */
    private static final double VERTICAL_EPSILON = 1e-6;

    private AreaOverlayRenderer() {}

    /**
     * The polylines an area draws.
     *
     * @param area   the ability's area
     * @param origin the hand, where a line or cone starts
     * @param point  the aim point
     * @return the polylines, empty for no area
     */
    static List<Vec3[]> areaLines(AbilityArea area, Vec3 origin, Vec3 point) {
        return switch (area.shape()) {
            case NONE -> List.of();
            case SPHERE -> sphereLines(point, area.size());
            case LINE -> List.<Vec3[]>of(new Vec3[] {origin, point});
            case CONE -> coneLines(origin, point.subtract(origin), area.size(), area.angle());
        };
    }

    /**
     * Three great circles around the center, one in each axis plane.
     *
     * @param center the sphere's center
     * @param radius the sphere's radius
     * @return the circles
     */
    private static List<Vec3[]> sphereLines(Vec3 center, double radius) {
        Vec3 x = new Vec3(1, 0, 0);
        Vec3 y = new Vec3(0, 1, 0);
        Vec3 z = new Vec3(0, 0, 1);
        return List.of(circle(center, x, y, radius), circle(center, x, z, radius), circle(center, y, z, radius));
    }

    /**
     * A cone from the apex along the axis: its base circle and the edges from
     * the apex to the base's rim.
     *
     * @param apex      the cone's apex
     * @param direction the cone's axis, any length
     * @param length    the cone's length
     * @param halfAngle the cone's half-angle in degrees
     * @return the base circle, then the edges
     */
    private static List<Vec3[]> coneLines(Vec3 apex, Vec3 direction, double length, double halfAngle) {
        Vec3 axis = direction.lengthSqr() < VERTICAL_EPSILON ? new Vec3(0, 0, 1) : direction.normalize();
        Vec3 across = axis.cross(new Vec3(0, 1, 0));
        if (across.lengthSqr() < VERTICAL_EPSILON) {
            across = new Vec3(1, 0, 0);
        }
        across = across.normalize();
        Vec3 up = across.cross(axis).normalize();
        Vec3 baseCenter = apex.add(axis.scale(length));
        double baseRadius = length * Math.tan(Math.toRadians(halfAngle));
        List<Vec3[]> lines = new ArrayList<>();
        lines.add(circle(baseCenter, across, up, baseRadius));
        for (int edge = 0; edge < CONE_EDGES; edge++) {
            double angle = Math.TAU * edge / CONE_EDGES;
            Vec3 rim = baseCenter.add(across.scale(baseRadius * Math.cos(angle)))
                    .add(up.scale(baseRadius * Math.sin(angle)));
            lines.add(new Vec3[] {apex, rim});
        }
        return lines;
    }

    /**
     * A circle in the plane two unit axes span.
     *
     * @param center the circle's center
     * @param first  one unit axis of its plane
     * @param second the other unit axis of its plane
     * @param radius the circle's radius
     * @return {@code CIRCLE_SEGMENTS + 1} points, the last closing the loop
     */
    private static Vec3[] circle(Vec3 center, Vec3 first, Vec3 second, double radius) {
        Vec3[] points = new Vec3[CIRCLE_SEGMENTS + 1];
        for (int i = 0; i <= CIRCLE_SEGMENTS; i++) {
            double angle = Math.TAU * i / CIRCLE_SEGMENTS;
            points[i] = center.add(first.scale(radius * Math.cos(angle))).add(second.scale(radius * Math.sin(angle)));
        }
        return points;
    }

    /**
     * Draws the area in the goo's color.
     *
     * @param poseStack    the pose stack
     * @param bufferSource the buffer source
     * @param camera       the render camera
     * @param area         the ability's area
     * @param origin       the hand
     * @param point        the aim point
     * @param rgb          the goo's highlight color
     */
    static void render(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, Camera camera,
                       AbilityArea area, Vec3 origin, Vec3 point, int rgb) {
        List<Vec3[]> lines = areaLines(area, origin, point);
        if (lines.isEmpty()) {
            return;
        }
        Vec3 cam = camera.position();
        float width = Minecraft.getInstance().getWindow().getAppropriateLineWidth();
        LineContext ctx = new LineContext(poseStack.last(), bufferSource.getBuffer(GooRenderTypes.LINES_GLOW));
        int color = ARGB.color(ALPHA, ARGB.red(rgb), ARGB.green(rgb), ARGB.blue(rgb));
        for (Vec3[] line : lines) {
            ctx.emitPolyline(cam, line, color, width);
        }
        bufferSource.endLastBatch();
    }
}
