package com.mercuriusxeno.goo.client.ber;

import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * An item's model broken into shards along its pixels (decision tiles-of-the-items-image):
 * each quad cut into texel cells in its own plane, each cell owned by one shard, so a flat
 * item breaks across its face and edges together and a block breaks across all six faces.
 * Positions are in the space the model's bounding box measures.
 */
final class ShardedModel {

    /** A model thinner than this share of its larger side lies flat, like a generated item. */
    static final float FLAT_DEPTH_SHARE = 0.25f;
    /** A quad deeper than this share of a texel along its thinnest axis cuts as one cell. */
    private static final float OBLIQUE_TEXELS = 0.25f;
    private static final float HALF = 0.5f;
    /** A quad corner low along both plane axes. */
    private static final int LOW_LOW = 0;
    /** A quad corner high along the plane's first axis. */
    private static final int HIGH_A = 1;
    /** A quad corner high along the plane's second axis. */
    private static final int HIGH_B = 2;
    /** A quad corner high along both plane axes. */
    private static final int HIGH_BOTH = HIGH_A + HIGH_B;

    private final Map<BakedQuad, QuadCut> cuts;
    private final int count;
    private final AABB box;
    private final float texel;
    private final List<List<float[]>> shardCells;

    private ShardedModel(Map<BakedQuad, QuadCut> cuts, int count, AABB box, float texel,
                         List<List<float[]>> shardCells) {
        this.cuts = cuts;
        this.count = count;
        this.box = box;
        this.texel = texel;
        this.shardCells = shardCells;
    }

    /**
     * Whether a cell of a quad shows the item, read at the cell center's UV.
     */
    @FunctionalInterface
    interface CellOpacity {
        /** Every cell counts as opaque. */
        CellOpacity ALL = (u, v) -> true;

        /**
         * @param u the texture U at the cell's center
         * @param v the texture V at the cell's center
         * @return true if the texel there is not fully transparent
         */
        boolean opaque(float u, float v);
    }

    /**
     * One quad as a layer submits it.
     *
     * @param quad    the baked quad, the key a shard's collector finds its cut by
     * @param inGrid  its vertices in the space the bounding box measures
     * @param outward its face's outward direction in that space
     * @param opacity which of its cells show the item
     */
    record CapturedQuad(BakedQuad quad, List<QuadRectClipper.ClipVertex> inGrid, Vector3fc outward,
                        CellOpacity opacity) {
    }

    /**
     * One quad cut into texel cells in the plane of two axes, each cell's shard.
     *
     * @param axisA  the axis the cells' columns run along
     * @param axisB  the axis the cells' rows run along
     * @param minA   the quad's low edge along axis A
     * @param minB   the quad's low edge along axis B
     * @param sizeA  one cell's width along axis A
     * @param sizeB  one cell's width along axis B
     * @param cellsA the cells along axis A
     * @param owners each cell's shard row by row, {@link ItemShardCutter#NO_SHARD} where transparent
     */
    record QuadCut(int axisA, int axisB, float minA, float minB, float sizeA, float sizeB, int cellsA,
                   int[] owners) {

        /**
         * Returns the rectangles a shard's cells cover in this quad, one per run along a row.
         *
         * @param shard the shard
         * @return the runs' rectangles in the plane of axes A and B
         */
        List<QuadRectClipper.Rect> runs(int shard) {
            List<QuadRectClipper.Rect> runs = new ArrayList<>();
            int rows = owners.length / cellsA;
            for (int row = 0; row < rows; row++) {
                int column = 0;
                while (column < cellsA) {
                    if (owners[row * cellsA + column] != shard) {
                        column++;
                        continue;
                    }
                    int from = column;
                    while (column < cellsA && owners[row * cellsA + column] == shard) {
                        column++;
                    }
                    runs.add(new QuadRectClipper.Rect(minA + from * sizeA, minB + row * sizeB,
                            minA + column * sizeA, minB + (row + 1) * sizeB));
                }
            }
            return runs;
        }
    }

    /**
     * Every cell of every quad, in quad order: the point the cutter reads, its center on the
     * face, and its opacity.
     *
     * @param layouts each quad's cell layout, owners not yet assigned
     * @param centers each cell's point for the cutter
     * @param surface each cell's center on its face
     * @param opaque  each cell's opacity
     */
    private record CellSheet(List<QuadCut> layouts, List<float[]> centers, List<float[]> surface,
                             List<Boolean> opaque) {

        boolean[] opaqueCells() {
            boolean[] cells = new boolean[opaque.size()];
            for (int i = 0; i < cells.length; i++) {
                cells[i] = opaque.get(i);
            }
            return cells;
        }
    }

    /**
     * One quad's plane cut into cells: the two axes it spans, its bounds, and its cells' size.
     *
     * @param axisA  the axis the cells' columns run along
     * @param axisB  the axis the cells' rows run along
     * @param thin   the axis the quad is thin along, its normal
     * @param bounds the quad's bounds
     * @param cellsA the cells along axis A
     * @param cellsB the cells along axis B
     */
    private record QuadPlane(int axisA, int axisB, int thin, AxisBounds bounds, int cellsA, int cellsB) {

        float sizeA() {
            return bounds.extent(axisA) / cellsA;
        }

        float sizeB() {
            return bounds.extent(axisB) / cellsB;
        }
    }

    /**
     * Breaks a model into shards.
     *
     * @param quads         every quad the model's layers submit
     * @param box           the model's bounding box
     * @param texelsPerSpan the texels across the model's larger side, its sprite's width
     * @param seed          the item's seed
     * @return the sharded model
     */
    static ShardedModel cut(List<CapturedQuad> quads, AABB box, int texelsPerSpan, long seed) {
        float span = (float) Math.max(box.getXsize(), box.getYsize());
        float texel = span / Math.max(texelsPerSpan, 1);
        boolean flat = box.getZsize() < span * FLAT_DEPTH_SHARE;
        CellSheet sheet = new CellSheet(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        for (CapturedQuad quad : quads) {
            sheet.layouts().add(layOut(quad, texel, flat, sheet));
        }
        ItemShardCutter.Assignment assignment = ItemShardCutter.assign(seed,
                sheet.centers().toArray(new float[0][]), sheet.opaqueCells(), texel);
        return new ShardedModel(cutsOf(quads, sheet.layouts(), assignment), assignment.count(), box, texel,
                shardCellsOf(assignment, sheet.surface()));
    }

    /**
     * Hands each quad its cells' owners from the cutter's answer, in the order the cells were laid out.
     *
     * @param quads      the quads
     * @param layouts    each quad's cell layout
     * @param assignment every cell's shard, quad by quad
     * @return each quad's cut, found by the quad itself
     */
    private static Map<BakedQuad, QuadCut> cutsOf(List<CapturedQuad> quads, List<QuadCut> layouts,
                                                  ItemShardCutter.Assignment assignment) {
        Map<BakedQuad, QuadCut> cuts = new IdentityHashMap<>();
        int cell = 0;
        for (int q = 0; q < quads.size(); q++) {
            QuadCut layout = layouts.get(q);
            int[] owners = new int[layout.owners().length];
            System.arraycopy(assignment.owners(), cell, owners, 0, owners.length);
            cell += owners.length;
            cuts.put(quads.get(q).quad(), new QuadCut(layout.axisA(), layout.axisB(), layout.minA(), layout.minB(),
                    layout.sizeA(), layout.sizeB(), layout.cellsA(), owners));
        }
        return cuts;
    }

    /**
     * Gathers each shard's cells' centers on the model's faces.
     *
     * @param assignment every cell's shard
     * @param surface    every cell's center on its face
     * @return each shard's cells' centers
     */
    private static List<List<float[]>> shardCellsOf(ItemShardCutter.Assignment assignment, List<float[]> surface) {
        List<List<float[]>> shardCells = new ArrayList<>(assignment.count());
        for (int shard = 0; shard < assignment.count(); shard++) {
            shardCells.add(new ArrayList<>());
        }
        for (int i = 0; i < assignment.owners().length; i++) {
            if (assignment.owners()[i] != ItemShardCutter.NO_SHARD) {
                shardCells.get(assignment.owners()[i]).add(surface.get(i));
            }
        }
        return shardCells;
    }

    /**
     * Lays one quad's cells out and appends each to the sheet.
     *
     * @param quad  the quad
     * @param texel one texel's width
     * @param flat  whether the model lies flat, its thin axis collapsed so a face and its
     *              edges break together
     * @param sheet the sheet receiving each cell
     * @return the quad's cell layout, owners not yet assigned
     */
    private static QuadCut layOut(CapturedQuad quad, float texel, boolean flat, CellSheet sheet) {
        QuadPlane plane = planeOf(quad.inGrid(), texel);
        for (int row = 0; row < plane.cellsB(); row++) {
            for (int column = 0; column < plane.cellsA(); column++) {
                float[] at = new float[AxisBounds.AXES];
                at[plane.axisA()] = plane.bounds().low(plane.axisA()) + (column + HALF) * plane.sizeA();
                at[plane.axisB()] = plane.bounds().low(plane.axisB()) + (row + HALF) * plane.sizeB();
                at[plane.thin()] = plane.bounds().low(plane.thin()) + plane.bounds().extent(plane.thin()) * HALF;
                sheet.surface().add(at);
                sheet.centers().add(inwardOf(at, quad.outward(), texel, flat));
                float[] uv = uvAt(quad.inGrid(), plane.axisA(), plane.axisB(), at[plane.axisA()], at[plane.axisB()]);
                sheet.opaque().add(quad.opacity().opaque(uv[0], uv[1]));
            }
        }
        return new QuadCut(plane.axisA(), plane.axisB(), plane.bounds().low(plane.axisA()),
                plane.bounds().low(plane.axisB()), plane.sizeA(), plane.sizeB(), plane.cellsA(),
                new int[plane.cellsA() * plane.cellsB()]);
    }

    /**
     * Finds the plane a quad spans and how many texel cells cover it; a quad slanted across
     * its thinnest axis cuts as one cell.
     *
     * @param vertices the quad's vertices
     * @param texel    one texel's width
     * @return the quad's plane
     */
    private static QuadPlane planeOf(List<QuadRectClipper.ClipVertex> vertices, float texel) {
        AxisBounds bounds = boundsOf(vertices);
        int thin = bounds.thinnest();
        int axisA = thin == QuadRectClipper.X ? QuadRectClipper.Y : QuadRectClipper.X;
        int axisB = thin == QuadRectClipper.Z ? QuadRectClipper.Y : QuadRectClipper.Z;
        boolean oblique = bounds.extent(thin) > texel * OBLIQUE_TEXELS;
        int cellsA = oblique ? 1 : Math.max(1, Math.round(bounds.extent(axisA) / texel));
        int cellsB = oblique ? 1 : Math.max(1, Math.round(bounds.extent(axisB) / texel));
        return new QuadPlane(axisA, axisB, thin, bounds, cellsA, cellsB);
    }

    private static AxisBounds boundsOf(List<QuadRectClipper.ClipVertex> vertices) {
        AxisBounds bounds = new AxisBounds();
        for (QuadRectClipper.ClipVertex vertex : vertices) {
            bounds.include(vertex.x(), vertex.y(), vertex.z());
        }
        return bounds;
    }

    /**
     * Returns a cell's point for the cutter: its center a half texel in from its face, so the
     * cells of faces meeting at an edge sit together, the thin axis collapsed on a flat model.
     *
     * @param at      the cell's center on its face
     * @param outward the face's outward direction
     * @param texel   one texel's width
     * @param flat    whether the model lies flat
     * @return the point
     */
    private static float[] inwardOf(float[] at, Vector3fc outward, float texel, boolean flat) {
        float step = texel * HALF;
        return new float[] {
            at[QuadRectClipper.X] - Math.signum(outward.x()) * step,
            at[QuadRectClipper.Y] - Math.signum(outward.y()) * step,
            flat ? 0f : at[QuadRectClipper.Z] - Math.signum(outward.z()) * step,
        };
    }

    /**
     * Interpolates a quad's UV at a point of its plane, bilinear between its four corners.
     *
     * @param vertices the quad's vertices
     * @param axisA    the plane's first axis
     * @param axisB    the plane's second axis
     * @param a        the point along axis A
     * @param b        the point along axis B
     * @return the U and V there
     */
    static float[] uvAt(List<QuadRectClipper.ClipVertex> vertices, int axisA, int axisB, float a, float b) {
        AxisBounds bounds = boundsOf(vertices);
        QuadRectClipper.ClipVertex[] corners = cornersOf(vertices, bounds, axisA, axisB);
        float ta = fractionAlong(bounds, axisA, a);
        float tb = fractionAlong(bounds, axisB, b);
        return new float[] {
            bilerp(corners[LOW_LOW].u(), corners[HIGH_A].u(), corners[HIGH_B].u(), corners[HIGH_BOTH].u(), ta, tb),
            bilerp(corners[LOW_LOW].v(), corners[HIGH_A].v(), corners[HIGH_B].v(), corners[HIGH_BOTH].v(), ta, tb),
        };
    }

    /**
     * Sorts a quad's vertices by the corner they sit at: low or high along each plane axis.
     *
     * @param vertices the quad's vertices
     * @param bounds   the quad's bounds
     * @param axisA    the plane's first axis
     * @param axisB    the plane's second axis
     * @return the vertices at {@link #LOW_LOW}, {@link #HIGH_A}, {@link #HIGH_B} and {@link #HIGH_BOTH}
     */
    private static QuadRectClipper.ClipVertex[] cornersOf(List<QuadRectClipper.ClipVertex> vertices,
                                                          AxisBounds bounds, int axisA, int axisB) {
        QuadRectClipper.ClipVertex[] corners = new QuadRectClipper.ClipVertex[BakedQuad.VERTEX_COUNT];
        for (int i = 0; i < corners.length; i++) {
            corners[i] = vertices.get(i % vertices.size());
        }
        float middleA = bounds.low(axisA) + bounds.extent(axisA) * HALF;
        float middleB = bounds.low(axisB) + bounds.extent(axisB) * HALF;
        for (QuadRectClipper.ClipVertex vertex : vertices) {
            int corner = (vertex.along(axisA) > middleA ? HIGH_A : LOW_LOW)
                    + (vertex.along(axisB) > middleB ? HIGH_B : LOW_LOW);
            corners[corner] = vertex;
        }
        return corners;
    }

    private static float fractionAlong(AxisBounds bounds, int axis, float at) {
        return bounds.extent(axis) == 0f ? 0f : (at - bounds.low(axis)) / bounds.extent(axis);
    }

    private static float bilerp(float lowLow, float highLow, float lowHigh, float highHigh, float ta, float tb) {
        float nearB = lowLow + (highLow - lowLow) * ta;
        float farB = lowHigh + (highHigh - lowHigh) * ta;
        return nearB + (farB - nearB) * tb;
    }

    /**
     * @param quad a quad a layer submits
     * @return its cut, or null for a quad the model was not cut from
     */
    @Nullable QuadCut cutOf(BakedQuad quad) {
        return cuts.get(quad);
    }

    /**
     * @return the shards the model breaks into
     */
    int count() {
        return count;
    }

    /**
     * @return the model's bounding box
     */
    AABB box() {
        return box;
    }

    /**
     * @return one texel's width
     */
    float texel() {
        return texel;
    }

    /**
     * @return whether the model lies flat, like a generated item, rather than standing like a block
     */
    boolean flat() {
        return box.getZsize() < Math.max(box.getXsize(), box.getYsize()) * FLAT_DEPTH_SHARE;
    }

    /**
     * @return the model's larger side in X and Y, the width the head scales to
     */
    float span() {
        return (float) Math.max(box.getXsize(), box.getYsize());
    }

    /**
     * @param shard the shard
     * @return the centers of its cells on the model's faces
     */
    List<float[]> cellsOf(int shard) {
        return shardCells.get(shard);
    }
}
