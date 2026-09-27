package com.mercuriusxeno.goo.client.ber;

import java.util.ArrayList;
import java.util.List;

/**
 * An item's face laid out in the space its model's bounding box measures, with the shard
 * map over its texels: where each texel lies, and which shard a point of the model belongs
 * to (decision tiles-of-the-items-image).
 *
 * @param bounds the face's rectangle in XY, the texel grid spanning it
 * @param map    which shard each texel belongs to
 */
record ShardFace(QuadRectClipper.Rect bounds, ItemShardCutter.ShardMap map) {

    private static final float HALF = 0.5f;

    /**
     * @return one texel's width along X
     */
    float texelWidth() {
        return (bounds.maxX() - bounds.minX()) / map.columns();
    }

    /**
     * @return one texel's height along Y
     */
    float texelHeight() {
        return (bounds.maxY() - bounds.minY()) / map.rows();
    }

    /**
     * Returns the rectangles a shard's texel runs cover, the pieces its face quads cut to.
     *
     * @param shard the shard
     * @return one rectangle per run
     */
    List<QuadRectClipper.Rect> runRects(int shard) {
        List<QuadRectClipper.Rect> rects = new ArrayList<>();
        for (ItemShardCutter.TexelRun run : map.runs(shard)) {
            float minY = bounds.minY() + run.row() * texelHeight();
            rects.add(new QuadRectClipper.Rect(bounds.minX() + run.fromColumn() * texelWidth(), minY,
                    bounds.minX() + run.toColumn() * texelWidth(), minY + texelHeight()));
        }
        return rects;
    }

    /**
     * Returns the shard owning the texel under a point, the nearest texel for a point off the face.
     *
     * @param x the X
     * @param y the Y
     * @return the shard
     */
    int ownerAt(float x, float y) {
        int column = Math.clamp((int) Math.floor((x - bounds.minX()) / texelWidth()), 0, map.columns() - 1);
        int row = Math.clamp((int) Math.floor((y - bounds.minY()) / texelHeight()), 0, map.rows() - 1);
        return map.ownerOf(column, row);
    }

    /**
     * Returns a shard's centroid in the face's space.
     *
     * @param shard the shard
     * @return the centroid's X and Y
     */
    float[] centroid(int shard) {
        double[] texels = map.centroid(shard);
        return new float[] {bounds.minX() + (float) texels[0] * texelWidth(),
            bounds.minY() + (float) texels[1] * texelHeight()};
    }

    /**
     * @return the face's center X
     */
    float centerX() {
        return (bounds.minX() + bounds.maxX()) * HALF;
    }

    /**
     * @return the face's center Y
     */
    float centerY() {
        return (bounds.minY() + bounds.maxY()) * HALF;
    }

    /**
     * @return the face's larger side, the width the head scales to
     */
    float span() {
        return Math.max(bounds.maxX() - bounds.minX(), bounds.maxY() - bounds.minY());
    }
}
