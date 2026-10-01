package com.mercuriusxeno.goo.client.radial;

import java.util.ArrayList;
import java.util.List;

/**
 * Tessellates a {@link PetalMask.Petal} into quads the GUI draws live: a
 * fill grid whose every cell carries the fraction of one fluid sprite tile
 * it covers, so the fill samples the animated sprite on the block atlas,
 * and a strip along the petal's outline for its solid edge.
 * decision petals-render-the-live-fluid
 * decision wedges-take-a-solid-edge
 *
 * <p>Positions are the wheel's normalized coordinates (center 0, rim 1, y
 * down positive). Every quad winds the way the GUI's own blits do.
 */
final class PetalMesh {

    /** The width of a petal's edge, a fiftieth of the wheel's radius. */
    static final double EDGE_THICKNESS = 0.02;
    /** Sprite tiles the fill repeats from the hub to the cap's tip. */
    static final int RADIAL_TILES = 2;
    /** Grid rows each radial tile is cut into, so the cap's curve reads smooth. */
    private static final int ROWS_PER_TILE = 2;
    /** The widest angle one grid column spans, so the cap's curve reads smooth. */
    private static final double MAX_COLUMN_ARC = Math.toRadians(3.0);
    /** Segments each of the outline's four sides is cut into for the edge strip. */
    static final int OUTLINE_SEGMENTS = 12;
    private static final double HALF = 0.5;

    private PetalMesh() {
    }

    /**
     * A quad's corner: where it sits and where in one sprite tile it samples.
     *
     * @param x normalized x
     * @param y normalized y, down positive
     * @param u the fraction across the sprite tile, 0 to 1
     * @param v the fraction down the sprite tile, 0 to 1
     */
    record Vertex(double x, double y, double u, double v) {
    }

    /**
     * Four corners drawn as one GUI quad.
     *
     * @param a the first corner
     * @param b the second corner
     * @param c the third corner
     * @param d the fourth corner
     */
    record Quad(Vertex a, Vertex b, Vertex c, Vertex d) {

        /**
         * The corners in draw order.
         *
         * @return the four corners
         */
        List<Vertex> corners() {
            return List.of(a, b, c, d);
        }

        /**
         * The quad wound the way {@code GuiGraphicsExtractor}'s blits wind theirs,
         * which read a negative shoelace sum in screen coordinates.
         *
         * @return this quad, or its corners reversed
         */
        Quad wound() {
            double twiceArea = cross(a, b) + cross(b, c) + cross(c, d) + cross(d, a);
            return twiceArea <= 0 ? this : new Quad(d, c, b, a);
        }

        private static double cross(Vertex from, Vertex to) {
            return from.x() * to.y() - to.x() * from.y();
        }
    }

    /**
     * The fill grid: columns across the petal's angle, rows from the inner
     * arc to the cap, each tile of {@link #RADIAL_TILES} rows and of as many
     * columns as keep a tile about square sampling one sprite tile whole.
     *
     * @param petal the petal
     * @return the fill's quads
     */
    static List<Quad> fill(PetalMask.Petal petal) {
        int angularTiles = angularTiles(petal);
        int columnsPerTile = Math.max(1, (int) Math.ceil(petal.arc() / angularTiles / MAX_COLUMN_ARC));
        int columns = angularTiles * columnsPerTile;
        int rows = RADIAL_TILES * ROWS_PER_TILE;
        List<Quad> quads = new ArrayList<>(columns * rows);
        for (int column = 0; column < columns; column++) {
            for (int row = 0; row < rows; row++) {
                quads.add(cell(petal, new Cell(column, row, columns, columnsPerTile)));
            }
        }
        return quads;
    }

    /**
     * One grid cell's place: its column and row, and the grid's shape.
     *
     * @param column         the cell's column, from the start edge
     * @param row            the cell's row, from the inner arc
     * @param columns        the grid's column count
     * @param columnsPerTile the columns one sprite tile spans
     */
    private record Cell(int column, int row, int columns, int columnsPerTile) {
    }

    private static Quad cell(PetalMask.Petal petal, Cell cell) {
        int rows = RADIAL_TILES * ROWS_PER_TILE;
        double u0 = (double) (cell.column() % cell.columnsPerTile()) / cell.columnsPerTile();
        double u1 = u0 + 1.0 / cell.columnsPerTile();
        double v0 = (double) (cell.row() % ROWS_PER_TILE) / ROWS_PER_TILE;
        double v1 = v0 + 1.0 / ROWS_PER_TILE;
        double angle0 = petal.start() + petal.arc() * cell.column() / cell.columns();
        double angle1 = petal.start() + petal.arc() * (cell.column() + 1) / cell.columns();
        double s0 = (double) cell.row() / rows;
        double s1 = (double) (cell.row() + 1) / rows;
        return new Quad(gridVertex(petal, angle0, s0, u0, v1), gridVertex(petal, angle0, s1, u0, v0),
                gridVertex(petal, angle1, s1, u1, v0), gridVertex(petal, angle1, s0, u1, v1)).wound();
    }

    private static Vertex gridVertex(PetalMask.Petal petal, double angle, double span, double u, double v) {
        PetalMask.Point point = PetalMask.Point.polar(angle,
                petal.inner() + (petal.reach(angle) - petal.inner()) * span);
        return new Vertex(point.x(), point.y(), u, v);
    }

    /**
     * How many sprite tiles the fill repeats across the petal's angle: as
     * many as keep a tile's width at mid radius near its radial length.
     *
     * @param petal the petal
     * @return the tile count, at least one
     */
    static int angularTiles(PetalMask.Petal petal) {
        double tileLength = (petal.outer() - petal.inner()) / RADIAL_TILES;
        double midWidth = petal.arc() * (petal.inner() + petal.outer()) * HALF;
        return Math.max(1, (int) Math.round(midWidth / tileLength));
    }

    /**
     * The edge strip: one quad per outline segment, reaching the edge's
     * thickness inward from the boundary.
     *
     * @param petal     the petal
     * @param thickness the edge's width in normalized units
     * @return the edge's quads
     */
    static List<Quad> edge(PetalMask.Petal petal, double thickness) {
        List<PetalMask.Point> outline = petal.outline(OUTLINE_SEGMENTS);
        double inward = Math.signum(signedArea(outline)) * thickness;
        List<Quad> quads = new ArrayList<>(outline.size());
        for (int i = 0; i < outline.size(); i++) {
            PetalMask.Point from = outline.get(i);
            PetalMask.Point to = outline.get((i + 1) % outline.size());
            double length = Math.hypot(to.x() - from.x(), to.y() - from.y());
            if (length == 0) {
                continue;
            }
            double normalX = -(to.y() - from.y()) / length * inward;
            double normalY = (to.x() - from.x()) / length * inward;
            quads.add(new Quad(new Vertex(from.x(), from.y(), 0, 0), new Vertex(to.x(), to.y(), 0, 0),
                    new Vertex(to.x() + normalX, to.y() + normalY, 0, 0),
                    new Vertex(from.x() + normalX, from.y() + normalY, 0, 0)).wound());
        }
        return quads;
    }

    private static double signedArea(List<PetalMask.Point> outline) {
        double sum = 0;
        for (int i = 0; i < outline.size(); i++) {
            PetalMask.Point from = outline.get(i);
            PetalMask.Point to = outline.get((i + 1) % outline.size());
            sum += from.x() * to.y() - to.x() * from.y();
        }
        return sum;
    }
}
