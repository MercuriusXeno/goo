package com.mercuriusxeno.goo.client.ber;

import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.util.ARGB;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import java.util.ArrayList;
import java.util.List;

/**
 * Cuts a quad to an XY rectangle, the prism x and y bounded and z free, so a shard's run of
 * texels draws exactly its own piece of the item's face (decision tiles-of-the-items-image).
 * A cut edge interpolates position, UV and color.
 */
final class QuadRectClipper {

    /** The extent under which a clipped piece counts as collapsed onto the rectangle's edge. */
    private static final float COLLAPSED = 1e-5f;
    private static final int TRIANGLE = 3;
    /** Each fanned quad takes two more corners of the polygon past the last quad's. */
    private static final int FAN_STEP = 2;
    /** Keeps the side of a plane at or above its bound. */
    private static final float KEEP_ABOVE = 1f;
    /** Keeps the side of a plane at or below its bound. */
    private static final float KEEP_BELOW = -1f;

    private QuadRectClipper() {
    }

    /**
     * One vertex of a quad being cut.
     *
     * @param x     the X position
     * @param y     the Y position
     * @param z     the Z position
     * @param u     the texture U
     * @param v     the texture V
     * @param color the baked ARGB color
     */
    record ClipVertex(float x, float y, float z, float u, float v, int color) {

        /**
         * Returns the point a fraction of the way from this vertex to another.
         *
         * @param other the far vertex
         * @param t     the fraction, zero at this vertex
         * @return the interpolated vertex
         */
        ClipVertex toward(ClipVertex other, float t) {
            return new ClipVertex(lerp(x, other.x, t), lerp(y, other.y, t), lerp(z, other.z, t),
                    lerp(u, other.u, t), lerp(v, other.v, t), ARGB.srgbLerp(t, color, other.color));
        }

        /**
         * Returns this vertex with its position carried through a transform.
         *
         * @param transform the affine transform
         * @return the moved vertex, its UV and color kept
         */
        ClipVertex moved(Matrix4fc transform) {
            Vector3f position = transform.transformPosition(x, y, z, new Vector3f());
            return new ClipVertex(position.x(), position.y(), position.z(), u, v, color);
        }

        private static float lerp(float from, float to, float t) {
            return from + (to - from) * t;
        }
    }

    /**
     * An axis-aligned rectangle in XY.
     *
     * @param minX the low X edge
     * @param minY the low Y edge
     * @param maxX the high X edge
     * @param maxY the high Y edge
     */
    record Rect(float minX, float minY, float maxX, float maxY) {
    }

    /**
     * Reads a baked quad's four vertices.
     *
     * @param quad the baked quad
     * @return its vertices, in winding order
     */
    static List<ClipVertex> verticesOf(BakedQuad quad) {
        List<ClipVertex> vertices = new ArrayList<>(BakedQuad.VERTEX_COUNT);
        for (int i = 0; i < BakedQuad.VERTEX_COUNT; i++) {
            Vector3fc position = quad.position(i);
            long uv = quad.packedUV(i);
            vertices.add(new ClipVertex(position.x(), position.y(), position.z(),
                    UVPair.unpackU(uv), UVPair.unpackV(uv), quad.bakedColors().color(i)));
        }
        return vertices;
    }

    /**
     * Returns the XY rectangle a set of vertices spans.
     *
     * @param vertices the vertices
     * @return their extent in X and Y
     */
    static Rect extentOf(List<ClipVertex> vertices) {
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (ClipVertex vertex : vertices) {
            minX = Math.min(minX, vertex.x());
            minY = Math.min(minY, vertex.y());
            maxX = Math.max(maxX, vertex.x());
            maxY = Math.max(maxY, vertex.y());
        }
        return new Rect(minX, minY, maxX, maxY);
    }

    /**
     * Cuts one quad spanning X and Y to a rectangle: whole when it lies inside, nothing when
     * it lies outside or only touches an edge, otherwise the piece inside as quads, a piece
     * beyond four corners fanned into several.
     *
     * @param quad the quad's four vertices
     * @param rect the rectangle to cut to
     * @return the quads of the piece inside the rectangle, four vertices each
     */
    static List<List<ClipVertex>> clip(List<ClipVertex> quad, Rect rect) {
        if (insideWhole(quad, rect)) {
            return List.of(quad);
        }
        List<ClipVertex> piece = clipAxis(quad, Axis.X, rect);
        piece = clipAxis(piece, Axis.Y, rect);
        if (piece.size() < TRIANGLE) {
            return List.of();
        }
        return fan(piece);
    }

    /** The two axes a rectangle bounds. */
    private enum Axis {
        X, Y;

        float of(ClipVertex vertex) {
            return this == X ? vertex.x() : vertex.y();
        }

        float min(Rect rect) {
            return this == X ? rect.minX() : rect.minY();
        }

        float max(Rect rect) {
            return this == X ? rect.maxX() : rect.maxY();
        }
    }

    private static boolean insideWhole(List<ClipVertex> quad, Rect rect) {
        for (ClipVertex vertex : quad) {
            if (vertex.x() < rect.minX() || vertex.x() > rect.maxX()
                    || vertex.y() < rect.minY() || vertex.y() > rect.maxY()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Cuts a polygon to a rectangle's bounds along one axis, dropping a piece collapsed onto
     * the rectangle's edge.
     *
     * @param polygon the polygon to cut
     * @param axis    the axis to cut along
     * @param rect    the rectangle to cut to
     * @return the piece inside the rectangle's bounds along the axis, or none
     */
    private static List<ClipVertex> clipAxis(List<ClipVertex> polygon, Axis axis, Rect rect) {
        if (polygon.isEmpty()) {
            return polygon;
        }
        List<ClipVertex> piece = clipPlane(clipPlane(polygon, axis, axis.min(rect), KEEP_ABOVE),
                axis, axis.max(rect), KEEP_BELOW);
        return piece.isEmpty() || extent(piece, axis) < COLLAPSED ? List.of() : piece;
    }

    /**
     * Returns how far a polygon spans along one axis.
     *
     * @param polygon the polygon
     * @param axis    the axis
     * @return its highest coordinate less its lowest
     */
    private static float extent(List<ClipVertex> polygon, Axis axis) {
        float low = Float.MAX_VALUE;
        float high = -Float.MAX_VALUE;
        for (ClipVertex vertex : polygon) {
            low = Math.min(low, axis.of(vertex));
            high = Math.max(high, axis.of(vertex));
        }
        return high - low;
    }

    /**
     * Keeps the side of one plane where side times (coordinate minus bound) is not negative,
     * one Sutherland-Hodgman pass.
     *
     * @param polygon the polygon to cut
     * @param axis    the axis the plane is normal to
     * @param bound   the plane's coordinate
     * @param side    {@link #KEEP_ABOVE} or {@link #KEEP_BELOW}
     * @return the vertices kept, with one cut vertex where an edge crosses the plane
     */
    private static List<ClipVertex> clipPlane(List<ClipVertex> polygon, Axis axis, float bound, float side) {
        List<ClipVertex> kept = new ArrayList<>(polygon.size() + 1);
        for (int i = 0; i < polygon.size(); i++) {
            ClipVertex from = polygon.get(i);
            ClipVertex to = polygon.get((i + 1) % polygon.size());
            float fromDistance = side * (axis.of(from) - bound);
            float toDistance = side * (axis.of(to) - bound);
            if (fromDistance >= 0f) {
                kept.add(from);
            }
            if (Math.signum(fromDistance) * Math.signum(toDistance) < 0f) {
                kept.add(from.toward(to, fromDistance / (fromDistance - toDistance)));
            }
        }
        return kept;
    }

    /**
     * Splits a convex polygon into quads sharing its first vertex; a triangle left over
     * repeats its last vertex.
     *
     * @param polygon the convex polygon, three corners or more
     * @return its quads, four vertices each
     */
    private static List<List<ClipVertex>> fan(List<ClipVertex> polygon) {
        List<List<ClipVertex>> quads = new ArrayList<>();
        ClipVertex hub = polygon.getFirst();
        int last = polygon.size() - 1;
        for (int i = 1; i < last; i += FAN_STEP) {
            quads.add(List.of(hub, polygon.get(i), polygon.get(i + 1), polygon.get(Math.min(i + FAN_STEP, last))));
        }
        return quads;
    }
}
