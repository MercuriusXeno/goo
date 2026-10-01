package com.mercuriusxeno.goo.client.radial;

import java.util.ArrayList;
import java.util.List;

/**
 * Tessellates a {@link PetalMask.Petal} into quads the GUI draws live: a
 * fill whose columns run from the inner arc out to points sampled around
 * the cap circle, so the tip reads round, each column cut along a fixed tile
 * grid so the fluid sprite tiles at one scale across the wheel rather than
 * warping to the petal; and a strip along the petal's outline for its solid edge.
 * decision petals-render-the-live-fluid
 * decision wedges-round-off-like-petals
 * decision wedges-take-a-solid-edge
 *
 * <p>Positions are the wheel's normalized coordinates (center 0, rim 1, y
 * down positive). Every quad winds the way the GUI's own blits do.
 */
final class PetalMesh {

    /** The width of a petal's edge, a fiftieth of the wheel's radius. */
    static final double EDGE_THICKNESS = 0.02;
    /**
     * One sprite tile's side in normalized units: a quarter of the wheel's
     * diameter, the scale the baked petals tiled at.
     */
    static final double TILE = 0.5;
    /** Where the tile grid starts: the wheel's top-left corner. */
    static final double TILE_ORIGIN = -1.0;
    /** Segments the cap's far side is cut into for the fill. */
    static final int CAP_SEGMENTS = 48;
    /** Segments each of the outline's straight and inner sides is cut into for the edge strip. */
    static final int OUTLINE_SEGMENTS = 12;
    /** The widest angle one fill column spans, so the inner arc reads smooth. */
    private static final double MAX_COLUMN_ARC = Math.toRadians(3.0);
    private static final double TWO_PI = 2.0 * Math.PI;
    /** The fewest corners a piece needs to cover any area. */
    private static final int TRIANGLE = 3;

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
     * Four corners drawn as one GUI quad; a triangle repeats its last corner.
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
     * The fill: one column per cap sample step, each from the inner arc to the
     * cap's far side, cut along the tile grid, each piece sampling the sprite
     * tile it sits in.
     *
     * @param petal the petal
     * @return the fill's quads
     */
    static List<Quad> fill(PetalMask.Petal petal) {
        List<Quad> quads = new ArrayList<>();
        for (List<PetalMask.Point> column : columns(petal)) {
            for (List<PetalMask.Point> piece : cutAlongTiles(column)) {
                addPiece(quads, piece);
            }
        }
        return quads;
    }

    /**
     * The fill's columns, each a convex quadrilateral: two points on the inner
     * arc and the two cap samples straight out from them. A column wider than
     * {@link #MAX_COLUMN_ARC} at the inner arc splits along the cap chord.
     *
     * @param petal the petal
     * @return each column's corners in order
     */
    static List<List<PetalMask.Point>> columns(PetalMask.Petal petal) {
        List<PetalMask.Point> cap = petal.capSamples(CAP_SEGMENTS);
        List<List<PetalMask.Point>> columns = new ArrayList<>();
        double previousAngle = petal.start();
        for (int i = 0; i + 1 < cap.size(); i++) {
            boolean last = i + 1 == cap.size() - 1;
            double nextAngle = last ? petal.start() + petal.arc()
                    : unwrapNear(RadialWheel.angleOf(cap.get(i + 1).x(), cap.get(i + 1).y()), previousAngle);
            int splits = Math.max(1, (int) Math.ceil((nextAngle - previousAngle) / MAX_COLUMN_ARC));
            for (int split = 0; split < splits; split++) {
                double from = (double) split / splits;
                double to = (double) (split + 1) / splits;
                columns.add(List.of(
                        PetalMask.Point.polar(previousAngle + (nextAngle - previousAngle) * from, petal.inner()),
                        along(cap.get(i), cap.get(i + 1), from),
                        along(cap.get(i), cap.get(i + 1), to),
                        PetalMask.Point.polar(previousAngle + (nextAngle - previousAngle) * to, petal.inner())));
            }
            previousAngle = nextAngle;
        }
        return columns;
    }

    private static double unwrapNear(double angle, double reference) {
        return reference + Math.IEEEremainder(angle - reference, TWO_PI);
    }

    private static PetalMask.Point along(PetalMask.Point from, PetalMask.Point to, double fraction) {
        return new PetalMask.Point(from.x() + (to.x() - from.x()) * fraction,
                from.y() + (to.y() - from.y()) * fraction);
    }

    /**
     * Cuts a convex polygon along every tile grid line crossing it.
     *
     * @param polygon the polygon's corners in order
     * @return the pieces, each inside one tile
     */
    static List<List<PetalMask.Point>> cutAlongTiles(List<PetalMask.Point> polygon) {
        List<List<PetalMask.Point>> pieces = new ArrayList<>();
        for (List<PetalMask.Point> strip : cutAlong(polygon, true)) {
            pieces.addAll(cutAlong(strip, false));
        }
        return pieces;
    }

    private static List<List<PetalMask.Point>> cutAlong(List<PetalMask.Point> polygon, boolean alongX) {
        List<List<PetalMask.Point>> pieces = new ArrayList<>();
        double low = Double.MAX_VALUE;
        double high = -Double.MAX_VALUE;
        for (PetalMask.Point point : polygon) {
            low = Math.min(low, coordinate(point, alongX));
            high = Math.max(high, coordinate(point, alongX));
        }
        List<PetalMask.Point> rest = polygon;
        for (double line = tileLineAbove(low); line < high && rest.size() >= TRIANGLE; line += TILE) {
            pieces.add(keepSide(rest, alongX, line, true));
            rest = keepSide(rest, alongX, line, false);
        }
        pieces.add(rest);
        pieces.removeIf(piece -> piece.size() < TRIANGLE);
        return pieces;
    }

    private static double tileLineAbove(double value) {
        return TILE_ORIGIN + (Math.floor((value - TILE_ORIGIN) / TILE) + 1) * TILE;
    }

    private static double coordinate(PetalMask.Point point, boolean alongX) {
        return alongX ? point.x() : point.y();
    }

    /**
     * The part of a convex polygon on one side of a grid line.
     *
     * @param polygon the polygon's corners in order
     * @param alongX  true for a vertical line at x, false for a horizontal one at y
     * @param line    the line's coordinate
     * @param below   true to keep the side under the line, false the side over it
     * @return the kept part's corners in order
     */
    private static List<PetalMask.Point> keepSide(List<PetalMask.Point> polygon, boolean alongX, double line,
                                                  boolean below) {
        List<PetalMask.Point> kept = new ArrayList<>();
        for (int i = 0; i < polygon.size(); i++) {
            PetalMask.Point from = polygon.get(i);
            PetalMask.Point to = polygon.get((i + 1) % polygon.size());
            double fromSide = coordinate(from, alongX) - line;
            double toSide = coordinate(to, alongX) - line;
            boolean fromKept = below ? fromSide <= 0 : fromSide >= 0;
            if (fromKept) {
                kept.add(from);
            }
            if (fromSide * toSide < 0) {
                kept.add(along(from, to, fromSide / (fromSide - toSide)));
            }
        }
        return kept;
    }

    private static void addPiece(List<Quad> quads, List<PetalMask.Point> piece) {
        double centerX = 0;
        double centerY = 0;
        for (PetalMask.Point point : piece) {
            centerX += point.x() / piece.size();
            centerY += point.y() / piece.size();
        }
        double tileX = TILE_ORIGIN + Math.floor((centerX - TILE_ORIGIN) / TILE) * TILE;
        double tileY = TILE_ORIGIN + Math.floor((centerY - TILE_ORIGIN) / TILE) * TILE;
        Vertex anchor = inTile(piece.getFirst(), tileX, tileY);
        for (int i = 1; i + 1 < piece.size(); i++) {
            Vertex next = inTile(piece.get(i + 1), tileX, tileY);
            quads.add(new Quad(anchor, inTile(piece.get(i), tileX, tileY), next, next).wound());
        }
    }

    private static Vertex inTile(PetalMask.Point point, double tileX, double tileY) {
        return new Vertex(point.x(), point.y(), clampUnit((point.x() - tileX) / TILE),
                clampUnit((point.y() - tileY) / TILE));
    }

    private static double clampUnit(double value) {
        return Math.max(0.0, Math.min(1.0, value));
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
