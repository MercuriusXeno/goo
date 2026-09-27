package com.mercuriusxeno.goo.client.particle;

import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import java.util.ArrayList;
import java.util.List;

/**
 * A drip drawn as a cuboid out of the square quads a particle draws: each face
 * tiled with equal squares, each square turned so its front faces out of the
 * cuboid (decision tap-drop-swells-then-falls).
 */
final class DripCuboid {

    /** A square quad's front faces its local +Z; this turns +Z to world +Z. */
    static final Quaternionfc FACES_SOUTH = new Quaternionf();

    /** Turns a quad's front to world -Z. */
    static final Quaternionfc FACES_NORTH = new Quaternionf().rotateY((float) Math.PI);

    /** Turns a quad's front to world +X. */
    static final Quaternionfc FACES_EAST = new Quaternionf().rotateY((float) (Math.PI / 2.0));

    /** Turns a quad's front to world -X. */
    static final Quaternionfc FACES_WEST = new Quaternionf().rotateY((float) (-Math.PI / 2.0));

    /** Turns a quad's front to world +Y. */
    static final Quaternionfc FACES_UP = new Quaternionf().rotateX((float) (-Math.PI / 2.0));

    /** Turns a quad's front to world -Y. */
    static final Quaternionfc FACES_DOWN = new Quaternionf().rotateX((float) (Math.PI / 2.0));

    /** Half extents in a full extent. */
    private static final float HALVES = 2f;

    private DripCuboid() {
    }

    /**
     * The extent of a drip's cuboid: a square footprint centered on the drip
     * and a height rising from its bottom.
     *
     * @param bottomY   the cuboid's lowest y
     * @param halfWidth half the footprint's side
     * @param height    the cuboid's height
     */
    record Extent(double bottomY, float halfWidth, float height) {

        /**
         * @return the cuboid's highest y
         */
        double topY() {
            return bottomY + height;
        }
    }

    /**
     * One square quad of a face, centered at an offset from the drip.
     *
     * @param dx       the quad center's x offset from the drip
     * @param y        the quad center's y
     * @param dz       the quad center's z offset from the drip
     * @param rotation turns the quad's front out of the cuboid
     * @param halfSize the quad's half extent
     */
    record Tile(double dx, double y, double dz, Quaternionfc rotation, float halfSize) {
    }

    /**
     * Tiles every face of a cuboid with squares as wide as the cuboid's
     * shortest side, so a face twice as wide as tall takes two.
     *
     * @param extent the cuboid
     * @return the tiles, none for a cuboid with no volume
     */
    static List<Tile> tiles(Extent extent) {
        List<Tile> tiles = new ArrayList<>();
        float tileHalf = Math.min(extent.halfWidth(), extent.height() / HALVES);
        if (tileHalf <= 0f) {
            return tiles;
        }
        int across = Math.round(extent.halfWidth() / tileHalf);
        int up = Math.round(extent.height() / HALVES / tileHalf);
        for (int a = 0; a < across; a++) {
            double along = tileCenter(-extent.halfWidth(), tileHalf, a);
            addCaps(tiles, extent, tileHalf, along, across);
            addSides(tiles, extent, tileHalf, along, up);
        }
        return tiles;
    }

    /**
     * Adds the top and bottom tiles in one row of the footprint.
     *
     * @param tiles    the tiles so far
     * @param extent   the cuboid
     * @param tileHalf the tiles' half extent
     * @param along    the row's offset from the drip along x
     * @param across   tiles across the footprint
     */
    private static void addCaps(List<Tile> tiles, Extent extent, float tileHalf, double along, int across) {
        for (int b = 0; b < across; b++) {
            double other = tileCenter(-extent.halfWidth(), tileHalf, b);
            tiles.add(new Tile(along, extent.topY(), other, FACES_UP, tileHalf));
            tiles.add(new Tile(along, extent.bottomY(), other, FACES_DOWN, tileHalf));
        }
    }

    /**
     * Adds the four sides' tiles in one column across each side.
     *
     * @param tiles    the tiles so far
     * @param extent   the cuboid
     * @param tileHalf the tiles' half extent
     * @param along    the column's offset from the drip along the side
     * @param up       tiles up each side
     */
    private static void addSides(List<Tile> tiles, Extent extent, float tileHalf, double along, int up) {
        float halfWidth = extent.halfWidth();
        for (int b = 0; b < up; b++) {
            double y = tileCenter(extent.bottomY(), tileHalf, b);
            tiles.add(new Tile(along, y, halfWidth, FACES_SOUTH, tileHalf));
            tiles.add(new Tile(along, y, -halfWidth, FACES_NORTH, tileHalf));
            tiles.add(new Tile(halfWidth, y, along, FACES_EAST, tileHalf));
            tiles.add(new Tile(-halfWidth, y, along, FACES_WEST, tileHalf));
        }
    }

    private static double tileCenter(double start, float tileHalf, int index) {
        return start + tileHalf * (HALVES * index + 1);
    }
}
