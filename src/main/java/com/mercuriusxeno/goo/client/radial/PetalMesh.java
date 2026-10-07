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
    static final int CAP_SEGMENTS = 72;
    /** How short a step between outline points counts as no step at all. */
    private static final double SAME_POINT = 1e-9;
    /** How nearly opposite two segment normals sum to nothing, where a miter has no direction. */
    private static final double MITER_FLOOR = 1e-6;
    /** The smallest cosine a miter divides by: a sharp turn reaches at most twice the thickness. */
    private static final double MITER_LIMIT = 0.5;
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
                        innerPoint(petal, previousAngle + (nextAngle - previousAngle) * from),
                        along(cap.get(i), cap.get(i + 1), from),
                        along(cap.get(i), cap.get(i + 1), to),
                        innerPoint(petal, previousAngle + (nextAngle - previousAngle) * to)));
            }
            previousAngle = nextAngle;
        }
        return columns;
    }

    /**
     * The part of an axis-aligned square lying under a petal's face, as
     * quads whose corners carry where they sit within the square, so a
     * sprite drawn across them shows only inside the petal, cut at its
     * border. The square is intersected with each of the fill's convex
     * columns, so the cut follows exactly the face the fill draws.
     * decision icons-slide-in-from-behind-the-tip
     *
     * @param petal    the petal
     * @param centerX  the square's center x, normalized
     * @param centerY  the square's center y, normalized
     * @param halfSize half the square's side, normalized
     * @return the quads, u 0 to 1 left to right and v 0 to 1 top to bottom across the square; none when
     *         the square lies wholly outside the petal
     */
    static List<Quad> clipSquare(PetalMask.Petal petal, double centerX, double centerY, double halfSize) {
        double left = centerX - halfSize;
        double top = centerY - halfSize;
        double side = halfSize + halfSize;
        List<PetalMask.Point> square = List.of(new PetalMask.Point(left, top),
                new PetalMask.Point(left + side, top), new PetalMask.Point(left + side, top + side),
                new PetalMask.Point(left, top + side));
        List<Quad> quads = new ArrayList<>();
        for (List<PetalMask.Point> piece : cutToPetal(petal, square)) {
            for (int i = 1; i + 1 < piece.size(); i++) {
                Vertex next = inSquare(piece.get(i + 1), left, top, side);
                quads.add(new Quad(inSquare(piece.getFirst(), left, top, side), inSquare(piece.get(i), left, top,
                        side), next, next).wound());
            }
        }
        return quads;
    }

    /**
     * The part of a convex polygon lying under a petal's face, as untextured
     * quads, for a shape drawn in one color, such as a learned item's slash.
     * decision icons-slide-in-from-behind-the-tip
     *
     * @param petal   the petal
     * @param polygon the convex polygon's corners in order, normalized
     * @return the quads; none when the polygon lies wholly outside the petal
     */
    static List<Quad> clipConvex(PetalMask.Petal petal, List<PetalMask.Point> polygon) {
        List<Quad> quads = new ArrayList<>();
        for (List<PetalMask.Point> piece : cutToPetal(petal, polygon)) {
            for (int i = 1; i + 1 < piece.size(); i++) {
                Vertex next = vertexAt(piece.get(i + 1));
                quads.add(new Quad(vertexAt(piece.getFirst()), vertexAt(piece.get(i)), next, next).wound());
            }
        }
        return quads;
    }

    /**
     * A convex polygon cut to each of the fill's convex columns, so the
     * pieces cover exactly its part under the face the fill draws.
     *
     * @param petal   the petal
     * @param polygon the convex polygon's corners in order
     * @return each overlapping piece's corners in order
     */
    private static List<List<PetalMask.Point>> cutToPetal(PetalMask.Petal petal, List<PetalMask.Point> polygon) {
        List<List<PetalMask.Point>> pieces = new ArrayList<>();
        for (List<PetalMask.Point> column : columns(petal)) {
            List<PetalMask.Point> piece = intersectConvex(polygon, column);
            if (!piece.isEmpty()) {
                pieces.add(piece);
            }
        }
        return pieces;
    }

    /**
     * The intersection of two convex polygons: the subject cut by the
     * half-plane inside each of the clip's edges in turn.
     *
     * @param subject the polygon kept, its corners in order
     * @param clip    the convex polygon it is cut to, its corners in order
     * @return the intersection's corners in order, fewer than three when the two do not overlap
     */
    private static List<PetalMask.Point> intersectConvex(List<PetalMask.Point> subject, List<PetalMask.Point> clip) {
        double winding = Math.signum(signedArea(clip));
        if (winding == 0) {
            return List.of();
        }
        List<PetalMask.Point> kept = subject;
        for (int i = 0; i < clip.size() && kept.size() >= TRIANGLE; i++) {
            PetalMask.Point from = clip.get(i);
            PetalMask.Point to = clip.get((i + 1) % clip.size());
            if (isSamePoint(from, to)) {
                continue;
            }
            kept = keepInside(kept, from, to, winding);
        }
        return kept.size() >= TRIANGLE ? kept : List.of();
    }

    /**
     * The part of a convex polygon on the inner side of the line through an
     * edge of a polygon winding the given way.
     *
     * @param polygon the polygon's corners in order
     * @param from    the edge's start
     * @param to      the edge's end
     * @param winding the sign of the clipping polygon's shoelace sum
     * @return the kept part's corners in order
     */
    private static List<PetalMask.Point> keepInside(List<PetalMask.Point> polygon, PetalMask.Point from,
                                                    PetalMask.Point to, double winding) {
        List<PetalMask.Point> kept = new ArrayList<>();
        for (int i = 0; i < polygon.size(); i++) {
            PetalMask.Point here = polygon.get(i);
            PetalMask.Point next = polygon.get((i + 1) % polygon.size());
            double hereSide = winding * sideOf(from, to, here);
            double nextSide = winding * sideOf(from, to, next);
            if (hereSide >= 0) {
                kept.add(here);
            }
            if (hereSide * nextSide < 0) {
                kept.add(along(here, next, hereSide / (hereSide - nextSide)));
            }
        }
        return kept;
    }

    private static double sideOf(PetalMask.Point from, PetalMask.Point to, PetalMask.Point point) {
        return (to.x() - from.x()) * (point.y() - from.y()) - (to.y() - from.y()) * (point.x() - from.x());
    }

    private static Vertex inSquare(PetalMask.Point point, double left, double top, double side) {
        return new Vertex(point.x(), point.y(), clampUnit((point.x() - left) / side),
                clampUnit((point.y() - top) / side));
    }

    private static PetalMask.Point innerPoint(PetalMask.Petal petal, double angle) {
        return PetalMask.Point.polar(angle, petal.innerReach(angle));
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
     * The edge strip: one continuous ribbon the edge's thickness inward
     * from the boundary, each outline point paired with one inner point
     * mitered between its two segments, so neighboring quads share corners
     * and no sliver of fill shows between them where the boundary bends.
     * decision wedges-take-a-solid-edge
     *
     * @param petal     the petal
     * @param thickness the edge's width in normalized units
     * @param innerSide whether the ribbon runs along the inner side too; an
     *                  ability petal leaves it to the base it starts from, so
     *                  one border marks the join
     * @return the edge's quads
     */
    static List<Quad> edge(PetalMask.Petal petal, double thickness, boolean innerSide) {
        List<PetalMask.Point> outline = petal.outline(OUTLINE_SEGMENTS);
        double inward = Math.signum(signedArea(outline)) * thickness;
        List<PetalMask.Point> path = distinct(innerSide ? outline : withoutInnerSide(petal, outline));
        int count = path.size();
        List<PetalMask.Point> inner = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            inner.add(innerSide ? mitered(path.get((i + count - 1) % count), path.get(i), path.get((i + 1) % count),
                    inward) : openOffset(path, i, inward));
        }
        int segments = innerSide ? count : count - 1;
        List<Quad> quads = new ArrayList<>(segments);
        for (int i = 0; i < segments; i++) {
            int next = (i + 1) % count;
            quads.add(new Quad(vertexAt(path.get(i)), vertexAt(path.get(next)), vertexAt(inner.get(next)),
                    vertexAt(inner.get(i))).wound());
        }
        return quads;
    }

    /**
     * The outline as an open path that skips the inner side: up the end edge,
     * around the far boundary and down the start edge to where the inner side began.
     *
     * @param petal   the petal
     * @param outline the petal's closed outline, which opens with its inner side
     * @return the open path's points in order
     */
    private static List<PetalMask.Point> withoutInnerSide(PetalMask.Petal petal, List<PetalMask.Point> outline) {
        int innerPoints = petal.innerAngles(OUTLINE_SEGMENTS).size();
        List<PetalMask.Point> path = new ArrayList<>(outline.subList(innerPoints, outline.size()));
        path.add(outline.getFirst());
        return path;
    }

    /**
     * An open path's inner point: mitered between its two segments inside the
     * path, offset square along its one segment at either end, so the border
     * ends flush where it meets the base rather than in a point.
     *
     * @param path   the open path
     * @param index  the point's index
     * @param inward the thickness, signed toward the outline's inside
     * @return the inner point
     */
    private static PetalMask.Point openOffset(List<PetalMask.Point> path, int index, double inward) {
        PetalMask.Point point = path.get(index);
        if (index == 0 || index == path.size() - 1) {
            double[] normal = index == 0 ? normal(point, path.get(1)) : normal(path.get(index - 1), point);
            return new PetalMask.Point(point.x() + normal[0] * inward, point.y() + normal[1] * inward);
        }
        return mitered(path.get(index - 1), point, path.get(index + 1), inward);
    }

    /**
     * The point a ribbon's inner side passes through at an outline point: the
     * point offset inward along both segments' normals, so it lies the
     * thickness away from each, its reach capped at twice the thickness
     * where the outline turns sharply.
     *
     * @param before the outline point before
     * @param point  the outline point
     * @param after  the outline point after
     * @param inward the thickness, signed toward the outline's inside
     * @return the inner point
     */
    private static PetalMask.Point mitered(PetalMask.Point before, PetalMask.Point point, PetalMask.Point after,
                                           double inward) {
        double[] first = normal(before, point);
        double[] second = normal(point, after);
        double sumX = first[0] + second[0];
        double sumY = first[1] + second[1];
        double sumLength = Math.hypot(sumX, sumY);
        if (sumLength < MITER_FLOOR) {
            return new PetalMask.Point(point.x() + first[0] * inward, point.y() + first[1] * inward);
        }
        double cosine = (sumX * first[0] + sumY * first[1]) / sumLength;
        double reach = inward / Math.max(cosine, MITER_LIMIT);
        return new PetalMask.Point(point.x() + sumX / sumLength * reach, point.y() + sumY / sumLength * reach);
    }

    /**
     * The left-hand unit normal of the segment from one point to the next.
     *
     * @param from the segment's start
     * @param to   the segment's end
     * @return the normal's x and y
     */
    private static double[] normal(PetalMask.Point from, PetalMask.Point to) {
        double length = Math.hypot(to.x() - from.x(), to.y() - from.y());
        return new double[]{-(to.y() - from.y()) / length, (to.x() - from.x()) / length};
    }

    private static Vertex vertexAt(PetalMask.Point point) {
        return new Vertex(point.x(), point.y(), 0, 0);
    }

    /**
     * The outline with each run of repeated points kept once, so every segment has a direction.
     *
     * @param outline the closed outline's points
     * @return the points, no two neighbors alike, the last unlike the first
     */
    private static List<PetalMask.Point> distinct(List<PetalMask.Point> outline) {
        List<PetalMask.Point> kept = new ArrayList<>(outline.size());
        for (PetalMask.Point point : outline) {
            if (kept.isEmpty() || !isSamePoint(point, kept.getLast())) {
                kept.add(point);
            }
        }
        if (kept.size() > 1 && isSamePoint(kept.getFirst(), kept.getLast())) {
            kept.removeLast();
        }
        return kept;
    }

    private static boolean isSamePoint(PetalMask.Point one, PetalMask.Point other) {
        return Math.hypot(one.x() - other.x(), one.y() - other.y()) <= SAME_POINT;
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
