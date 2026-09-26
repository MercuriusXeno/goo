package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.RenderContext;
import org.joml.Vector3f;

/**
 * The one cone emitter and its basis math, serving the per-marker metal
 * spike trap and the in-flight metal dart (decision
 * render-context-is-the-one-emitter). Both extend a many-sided cone from a
 * base point along a direction and need a stable perpendicular + cross
 * basis to place vertices around the cone's circular base.
 *
 * <p>The basis is an array of six floats laid out as
 * {@code [perpX, perpY, perpZ, crossX, crossY, crossZ]}. Consumers index
 * into it through the public {@code PERP_X} / {@code CROSS_X} constants
 * to keep call sites self-documenting.
 */
public final class ConeGeometry {

    /** Index of the perpendicular X component in the basis array. */
    public static final int PERP_X = 0;
    /** Index of the perpendicular Y component in the basis array. */
    public static final int PERP_Y = 1;
    /** Index of the perpendicular Z component in the basis array. */
    public static final int PERP_Z = 2;
    /** Index of the cross-product X component in the basis array. */
    public static final int CROSS_X = 3;
    /** Index of the cross-product Y component in the basis array. */
    public static final int CROSS_Y = 4;
    /** Index of the cross-product Z component in the basis array. */
    public static final int CROSS_Z = 5;

    /**
     * Threshold for choosing a perpendicular seed vector. When the
     * direction is too close to the world Y axis, fall back to {1, 0, 0}
     * to avoid a degenerate cross product.
     */
    private static final float DIRECTION_THRESHOLD = 0.9f;

    /** The triangle corner that opens its base edge. */
    public static final int BASE_START = 0;
    /** The triangle corner that closes its base edge. */
    public static final int BASE_END = 1;
    /** The triangle corner at its apex, emitted twice. */
    public static final int APEX = 2;

    /** Two pi for angle computation. */
    private static final float TWO_PI = (float) (2 * Math.PI);

    /** Half, for the midpoint angle and the tip's U. */
    private static final float HALF = 0.5f;

    /**
     * A cone to emit: its base center, the unit direction to its tip, and
     * its size.
     *
     * @param baseX  base center X
     * @param baseY  base center Y
     * @param baseZ  base center Z
     * @param dirX   unit direction X, base to tip
     * @param dirY   unit direction Y, base to tip
     * @param dirZ   unit direction Z, base to tip
     * @param length the distance from base to tip
     * @param radius the base radius
     */
    public record Cone(float baseX, float baseY, float baseZ,
                       float dirX, float dirY, float dirZ,
                       float length, float radius) {
    }

    /**
     * Emits one corner of a triangle.
     */
    @FunctionalInterface
    public interface TriangleCorner {
        /**
         * Emits the corner at the given index.
         *
         * @param corner {@link #BASE_START}, {@link #BASE_END} or {@link #APEX}
         */
        void emit(int corner);
    }

    private ConeGeometry() {
    }

    /**
     * Computes orthonormal {perp, cross} basis vectors for the given
     * direction. Both output vectors are unit length and perpendicular to
     * the direction (and to each other).
     *
     * @param dirX cone direction X component (normalized)
     * @param dirY cone direction Y component (normalized)
     * @param dirZ cone direction Z component (normalized)
     * @return six-element basis array {@code [perpX, perpY, perpZ, crossX, crossY, crossZ]}
     */
    public static float[] computeBasis(float dirX, float dirY, float dirZ) {
        float[] perp = seedPerp(dirX, dirY, dirZ);
        orthonormalize(perp, dirX, dirY, dirZ);
        float crossX = dirY * perp[PERP_Z] - dirZ * perp[PERP_Y];
        float crossY = dirZ * perp[PERP_X] - dirX * perp[PERP_Z];
        float crossZ = dirX * perp[PERP_Y] - dirY * perp[PERP_X];
        return new float[]{perp[PERP_X], perp[PERP_Y], perp[PERP_Z], crossX, crossY, crossZ};
    }

    /**
     * Picks a seed perpendicular vector that avoids near-parallel
     * alignment with {@code dir}. Returns {-dirZ, 0, dirX} for most
     * directions; falls back to {1, 0, 0} when {@code dir} runs close to
     * the Y axis.
     * @param dirX cone direction X
     * @param dirY cone direction Y
     * @param dirZ cone direction Z
     * @return three-element seed perpendicular vector
     */
    private static float[] seedPerp(float dirX, float dirY, float dirZ) {
        if (Math.abs(dirY) < DIRECTION_THRESHOLD) {
            return new float[]{-dirZ, 0, dirX};
        }
        return new float[]{1, 0, 0};
    }

    /**
     * Gram-Schmidt orthonormalizes {@code perp} against {@code dir} in
     * place: subtract the projection onto {@code dir}, then normalize.
     * @param perp the perpendicular seed vector to normalize in place
     * @param dirX reference direction X
     * @param dirY reference direction Y
     * @param dirZ reference direction Z
     */
    private static void orthonormalize(float[] perp, float dirX, float dirY, float dirZ) {
        float dot = perp[PERP_X] * dirX + perp[PERP_Y] * dirY + perp[PERP_Z] * dirZ;
        perp[PERP_X] -= dot * dirX;
        perp[PERP_Y] -= dot * dirY;
        perp[PERP_Z] -= dot * dirZ;
        float len = (float) Math.sqrt(
                perp[PERP_X] * perp[PERP_X] + perp[PERP_Y] * perp[PERP_Y]
                        + perp[PERP_Z] * perp[PERP_Z]);
        perp[PERP_X] /= len;
        perp[PERP_Y] /= len;
        perp[PERP_Z] /= len;
    }

    /**
     * Emits a triangle as the quad the quad formats draw: both base
     * corners, then the apex twice.
     *
     * @param corner emits the corner at each index
     */
    public static void emitTriangle(TriangleCorner corner) {
        corner.emit(BASE_START);
        corner.emit(BASE_END);
        corner.emit(APEX);
        corner.emit(APEX);
    }

    /**
     * Emits a cone as one textured triangle per side, each shaded by the
     * basis direction midway between its base corners, the tip by the
     * cone's direction.
     *
     * @param ctx   the render context
     * @param cone  the cone to emit
     * @param basis the orthonormal basis placing the base corners
     * @param sides the number of triangular sides
     * @param color the ARGB color
     * @param uv    the sprite UV rectangle
     */
    public static void emitCone(RenderContext ctx, Cone cone, float[] basis,
                                int sides, int color, GooRenderUtil.UvRect uv) {
        float tipX = cone.baseX() + cone.dirX() * cone.length();
        float tipY = cone.baseY() + cone.dirY() * cone.length();
        float tipZ = cone.baseZ() + cone.dirZ() * cone.length();
        float uMid = (uv.u0() + uv.u1()) * HALF;
        for (int side = 0; side < sides; side++) {
            float a0 = TWO_PI * side / sides;
            float a1 = TWO_PI * (side + 1) / sides;
            float[] normal = segmentNormal(basis, (a0 + a1) * HALF);
            Vector3f edge0 = baseCorner(cone, basis, a0);
            Vector3f edge1 = baseCorner(cone, basis, a1);
            emitTriangle(corner -> {
                switch (corner) {
                    case BASE_START -> ctx.vertexColored(color, edge0.x, edge0.y, edge0.z,
                            uv.u0(), uv.v0(), normal[PERP_X], normal[PERP_Y], normal[PERP_Z]);
                    case BASE_END -> ctx.vertexColored(color, edge1.x, edge1.y, edge1.z,
                            uv.u1(), uv.v0(), normal[PERP_X], normal[PERP_Y], normal[PERP_Z]);
                    default -> ctx.vertexColored(color, tipX, tipY, tipZ,
                            uMid, uv.v1(), cone.dirX(), cone.dirY(), cone.dirZ());
                }
            });
        }
    }

    /**
     * The point on the cone's base circle at the given angle.
     *
     * @param cone  the cone
     * @param basis the orthonormal basis
     * @param angle the angle around the base, from perp toward cross
     * @return the corner
     */
    private static Vector3f baseCorner(Cone cone, float[] basis, float angle) {
        float cos = (float) Math.cos(angle) * cone.radius();
        float sin = (float) Math.sin(angle) * cone.radius();
        return new Vector3f(
                cone.baseX() + basis[PERP_X] * cos + basis[CROSS_X] * sin,
                cone.baseY() + basis[PERP_Y] * cos + basis[CROSS_Y] * sin,
                cone.baseZ() + basis[PERP_Z] * cos + basis[CROSS_Z] * sin);
    }

    /**
     * The face normal of a cone side: the basis direction at the side's
     * mid angle.
     *
     * @param basis the orthonormal basis
     * @param midA  the angle midway between the side's base corners
     * @return the normal {nx, ny, nz}
     */
    public static float[] segmentNormal(float[] basis, float midA) {
        float cosM = (float) Math.cos(midA);
        float sinM = (float) Math.sin(midA);
        return new float[]{
                basis[PERP_X] * cosM + basis[CROSS_X] * sinM,
                basis[PERP_Y] * cosM + basis[CROSS_Y] * sinM,
                basis[PERP_Z] * cosM + basis[CROSS_Z] * sinM,
        };
    }
}
