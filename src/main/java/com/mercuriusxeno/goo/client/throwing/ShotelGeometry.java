package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.RenderContext;
import org.joml.Vector3f;

/**
 * Blade's bone shotel as geometry about its hilt: a short grip below the hilt, and above it a flat blade sweeping
 * forward along the heading and curling back over itself like a sickle, widest by the hilt and narrowing to its
 * point, drawn on both faces in the goo's sprite.
 * decision blade-forms-a-bone-shotel-in-hand
 */
public final class ShotelGeometry {

    /** The segments the blade's curve is cut into. */
    static final int SEGMENTS = 12;
    /** The radius of the circle the blade's spine follows, in blocks. */
    static final float CURVE_RADIUS = 0.32f;
    /** How far round its circle the blade sweeps, in radians: past a half turn, so the point curls back. */
    static final float SWEEP = (float) (Math.PI * 1.15);
    /** The blade's width by the hilt, in blocks; it narrows to nothing at the point. */
    static final float ROOT_WIDTH = 0.07f;
    /** The grip's length below the hilt and its width, in blocks. */
    static final float GRIP_LENGTH = 0.16f;
    static final float GRIP_WIDTH = 0.035f;
    private static final float HALF = 0.5f;

    private ShotelGeometry() {
    }

    /**
     * Where the blade's spine stands a share of the way along it, about the hilt: on a circle rising from the hilt
     * and leaning forward along the heading.
     *
     * @param share   0 at the hilt, 1 at the point
     * @param heading the unit heading the shotel points along
     * @param up      the unit up, square to the heading
     * @return the point on the spine
     */
    static Vector3f spineAt(float share, Vector3f heading, Vector3f up) {
        float angle = SWEEP * share;
        float forward = CURVE_RADIUS * (float) Math.sin(angle);
        float rise = CURVE_RADIUS * (1f - (float) Math.cos(angle));
        return new Vector3f(heading).mul(forward).add(new Vector3f(up).mul(rise));
    }

    /**
     * The blade's half width a share of the way along it.
     *
     * @param share 0 at the hilt, 1 at the point
     * @return the half width in blocks
     */
    static float halfWidthAt(float share) {
        return ROOT_WIDTH * (1f - share) * HALF;
    }

    /**
     * Emits the shotel about the pose's origin, its hilt there.
     *
     * @param ctx     the render context
     * @param heading the unit heading the blade leans along
     * @param up      the unit up, square to the heading
     * @param uv      the goo sprite's rectangle
     */
    static void emit(RenderContext ctx, Vector3f heading, Vector3f up, GooRenderUtil.UvRect uv) {
        Vector3f flat = new Vector3f(heading).cross(up).normalize();
        for (int segment = 0; segment < SEGMENTS; segment++) {
            float from = (float) segment / SEGMENTS;
            float to = (float) (segment + 1) / SEGMENTS;
            Vector3f a = spineAt(from, heading, up);
            Vector3f b = spineAt(to, heading, up);
            Vector3f outA = outwardAt(from, heading, up);
            Vector3f outB = outwardAt(to, heading, up);
            emitQuad(ctx, flat, uv, new Vector3f[]{
                    new Vector3f(a).sub(new Vector3f(outA).mul(halfWidthAt(from))),
                    new Vector3f(b).sub(new Vector3f(outB).mul(halfWidthAt(to))),
                    new Vector3f(b).add(new Vector3f(outB).mul(halfWidthAt(to))),
                    new Vector3f(a).add(new Vector3f(outA).mul(halfWidthAt(from)))});
        }
        Vector3f gripEnd = new Vector3f(up).mul(-GRIP_LENGTH);
        Vector3f gripSide = new Vector3f(heading).mul(GRIP_WIDTH * HALF);
        emitQuad(ctx, flat, uv, new Vector3f[]{
                new Vector3f(gripSide).negate(), new Vector3f(gripEnd).sub(gripSide),
                new Vector3f(gripEnd).add(gripSide), new Vector3f(gripSide)});
    }

    // The unit direction square to the spine within the blade's plane, away from the curve's middle.
    private static Vector3f outwardAt(float share, Vector3f heading, Vector3f up) {
        float angle = SWEEP * share;
        return new Vector3f(heading).mul((float) Math.sin(angle)).add(new Vector3f(up).mul(-(float) Math.cos(angle)))
                .normalize();
    }

    // One quad on both faces, the sprite laid across it by its corners' order.
    private static void emitQuad(RenderContext ctx, Vector3f normal, GooRenderUtil.UvRect uv, Vector3f[] corners) {
        float[][] uvs = {{uv.u0(), uv.v0()}, {uv.u1(), uv.v0()}, {uv.u1(), uv.v1()}, {uv.u0(), uv.v1()}};
        for (int corner = 0; corner < corners.length; corner++) {
            Vector3f at = corners[corner];
            ctx.vertexColored(GooRenderUtil.OPAQUE_WHITE, at.x, at.y, at.z, uvs[corner][0], uvs[corner][1],
                    normal.x, normal.y, normal.z);
        }
        for (int corner = corners.length - 1; corner >= 0; corner--) {
            Vector3f at = corners[corner];
            ctx.vertexColored(GooRenderUtil.OPAQUE_WHITE, at.x, at.y, at.z, uvs[corner][0], uvs[corner][1],
                    -normal.x, -normal.y, -normal.z);
        }
    }
}
