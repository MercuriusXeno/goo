package com.mercuriusxeno.goo.client.ber;

import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import java.util.ArrayList;
import java.util.List;

/**
 * Cuts a melting item's baked quads into the tiles of a square grid over the model's XY,
 * the plane its flat face spans, so each tile draws its own piece of the item's image
 * (decision tiles-of-the-items-image). Each tile is a prism, x and y bounded and z free;
 * a cut edge interpolates position, UV and color.
 */
final class ItemTileClipper {

    /** The extent under which a clipped piece counts as collapsed onto a tile's edge. */
    private static final float COLLAPSED = 1e-5f;
    private static final float HALF = 0.5f;
    private static final int TRIANGLE = 3;
    /** Each fanned quad takes two more corners of the polygon past the last quad's. */
    private static final int FAN_STEP = 2;
    /** Keeps the side of a plane at or above its bound. */
    private static final float KEEP_ABOVE = 1f;
    /** Keeps the side of a plane at or below its bound. */
    private static final float KEEP_BELOW = -1f;

    private ItemTileClipper() {
    }

    /**
     * One vertex of a quad being cut, in model space.
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
     * A square grid of tiles over a model's XY.
     *
     * @param minX  the grid's low X edge
     * @param minY  the grid's low Y edge
     * @param span  the grid's side
     * @param count the tiles along each side
     */
    record TileGrid(float minX, float minY, float span, int count) {

        /**
         * Lays a grid over a model: a square as wide as the model's larger XY extent,
         * centered on it, so the tiles scale with the model to the head's width.
         *
         * @param box   the model's bounding box
         * @param count the tiles along each side
         * @return the grid
         */
        static TileGrid around(AABB box, int count) {
            float span = (float) Math.max(box.getXsize(), box.getYsize());
            float centerX = (float) (box.minX + box.maxX) * HALF;
            float centerY = (float) (box.minY + box.maxY) * HALF;
            return new TileGrid(centerX - span * HALF, centerY - span * HALF, span, count);
        }

        /**
         * @return one tile's side
         */
        float cell() {
            return span / count;
        }

        /**
         * Returns the tile at a grid index, counted along X, then along Y.
         *
         * @param index the tile index
         * @return the tile
         */
        Tile tile(int index) {
            return new Tile(this, index % count, index / count);
        }

        /**
         * Returns the column or row a coordinate belongs to: on a shared edge, the tile whose
         * low edge it lies on, or the last tile on the grid's high edge, so a quad lying flat
         * on a shared edge draws once.
         *
         * @param coordinate the coordinate along the axis
         * @param gridMin    the grid's low edge along the axis
         * @return the column or row index
         */
        private int indexAlong(float coordinate, float gridMin) {
            return Math.clamp((int) Math.floor((coordinate - gridMin) / cell()), 0, count - 1);
        }
    }

    /**
     * One tile of a grid.
     *
     * @param grid   the grid it belongs to
     * @param column its column, along X
     * @param row    its row, along Y
     */
    record Tile(TileGrid grid, int column, int row) {

        float minX() {
            return grid.minX() + column * grid.cell();
        }

        float maxX() {
            return minX() + grid.cell();
        }

        float minY() {
            return grid.minY() + row * grid.cell();
        }

        float maxY() {
            return minY() + grid.cell();
        }

        float centerX() {
            return minX() + grid.cell() * HALF;
        }

        float centerY() {
            return minY() + grid.cell() * HALF;
        }
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
     * Cuts one quad to a tile: whole when it lies inside, nothing when it lies outside,
     * otherwise the piece inside as quads, a piece beyond four corners fanned into several.
     *
     * @param quad the quad's four vertices
     * @param tile the tile to cut to
     * @return the quads of the piece inside the tile, four vertices each
     */
    static List<List<ClipVertex>> clip(List<ClipVertex> quad, Tile tile) {
        List<ClipVertex> piece = clipAxis(quad, Axis.X, tile);
        piece = clipAxis(piece, Axis.Y, tile);
        if (piece.size() < TRIANGLE) {
            return List.of();
        }
        if (insideWhole(quad, tile)) {
            return List.of(quad);
        }
        return fan(piece);
    }

    /** The two axes a tile bounds. */
    private enum Axis {
        X, Y;

        float of(ClipVertex vertex) {
            return this == X ? vertex.x() : vertex.y();
        }

        float min(Tile tile) {
            return this == X ? tile.minX() : tile.minY();
        }

        float max(Tile tile) {
            return this == X ? tile.maxX() : tile.maxY();
        }

        int indexOf(TileGrid grid, float coordinate) {
            return grid.indexAlong(coordinate, this == X ? grid.minX() : grid.minY());
        }

        int indexOf(Tile tile) {
            return this == X ? tile.column() : tile.row();
        }
    }

    private static boolean insideWhole(List<ClipVertex> quad, Tile tile) {
        for (ClipVertex vertex : quad) {
            if (vertex.x() < tile.minX() || vertex.x() > tile.maxX()
                    || vertex.y() < tile.minY() || vertex.y() > tile.maxY()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Cuts a polygon to a tile's bounds along one axis. A polygon flat in that axis, such
     * as an item's side face, belongs whole to the one tile its coordinate falls in; a
     * piece collapsed onto the tile's edge is dropped.
     *
     * @param polygon the polygon to cut
     * @param axis    the axis to cut along
     * @param tile    the tile to cut to
     * @return the piece inside the tile's bounds along the axis, or none
     */
    private static List<ClipVertex> clipAxis(List<ClipVertex> polygon, Axis axis, Tile tile) {
        if (polygon.isEmpty()) {
            return polygon;
        }
        if (extent(polygon, axis) < COLLAPSED) {
            return axis.indexOf(tile.grid(), axis.of(polygon.getFirst())) == axis.indexOf(tile) ? polygon : List.of();
        }
        List<ClipVertex> piece = clipPlane(clipPlane(polygon, axis, axis.min(tile), KEEP_ABOVE),
                axis, axis.max(tile), KEEP_BELOW);
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
