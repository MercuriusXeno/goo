package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.client.ber.QuadRectClipper.ClipVertex;
import com.mercuriusxeno.goo.client.ber.QuadRectClipper.Rect;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cutting a quad to a rectangle: the pieces cover the quad exactly with UVs and colors
 * carried along every cut, and a quad inside passes whole (decision tiles-of-the-items-image).
 */
class QuadRectClipperTest {

    private static final float EPSILON = 1e-5f;
    private static final float FRONT_Z = 8.5f / 16f;
    private static final float U0 = 0.25f;
    private static final float U_PER_X = 0.0625f;
    private static final float V0 = 0.5f;
    private static final float V_PER_Y = -0.0625f;
    private static final int WHITE = 0xFFFFFFFF;

    /** A vertex whose UV is the linear map of its position a sprite on the item atlas has. */
    private static ClipVertex mapped(float x, float y) {
        return new ClipVertex(x, y, FRONT_Z, U0 + U_PER_X * x, V0 + V_PER_Y * y, WHITE);
    }

    /** The shoelace area of a quad's XY footprint; a repeated vertex adds nothing. */
    private static float areaXY(List<ClipVertex> quad) {
        float twice = 0f;
        for (int i = 0; i < quad.size(); i++) {
            ClipVertex a = quad.get(i);
            ClipVertex b = quad.get((i + 1) % quad.size());
            twice += a.x() * b.y() - b.x() * a.y();
        }
        return Math.abs(twice) / 2f;
    }

    /** The cells of an n by n grid over the unit square. */
    private static List<Rect> unitCells(int n) {
        List<Rect> cells = new ArrayList<>();
        for (int row = 0; row < n; row++) {
            for (int column = 0; column < n; column++) {
                cells.add(new Rect((float) column / n, (float) row / n, (float) (column + 1) / n, (float) (row + 1) / n));
            }
        }
        return cells;
    }

    /** A full face cut to every cell: each piece inside its cell, areas summing to the face, UVs linear. */
    @Test
    void piecesOfAFullFaceCoverItExactly() {
        List<ClipVertex> face = List.of(mapped(0f, 0f), mapped(0f, 1f), mapped(1f, 1f), mapped(1f, 0f));
        float area = 0f;
        for (Rect cell : unitCells(4)) {
            List<List<ClipVertex>> pieces = QuadRectClipper.clip(face, cell);
            assertEquals(1, pieces.size());
            for (ClipVertex vertex : pieces.getFirst()) {
                assertTrue(vertex.x() >= cell.minX() - EPSILON && vertex.x() <= cell.maxX() + EPSILON);
                assertTrue(vertex.y() >= cell.minY() - EPSILON && vertex.y() <= cell.maxY() + EPSILON);
                assertEquals(U0 + U_PER_X * vertex.x(), vertex.u(), EPSILON);
                assertEquals(V0 + V_PER_Y * vertex.y(), vertex.v(), EPSILON);
            }
            area += areaXY(pieces.getFirst());
        }
        assertEquals(1f, area, EPSILON);
    }

    /** A diamond's slanted edges cut pentagons that fan into quads still summing to its area. */
    @Test
    void slantedEdgesFanIntoQuadsThatCoverTheQuad() {
        List<ClipVertex> diamond = List.of(mapped(0.5f, 0f), mapped(1f, 0.5f), mapped(0.5f, 1f), mapped(0f, 0.5f));
        float area = 0f;
        for (Rect cell : unitCells(5)) {
            for (List<ClipVertex> piece : QuadRectClipper.clip(diamond, cell)) {
                assertEquals(4, piece.size());
                area += areaXY(piece);
            }
        }
        assertEquals(0.5f, area, EPSILON);
        assertEquals(2, QuadRectClipper.clip(diamond, new Rect(0.2f, 0.2f, 0.4f, 0.4f)).size(),
                "the pentagon the diamond's edge cuts from this cell fans into two quads");
    }

    /** A cut vertex's color blends the two corners it lies between. */
    @Test
    void cutVertexBlendsTheColorsItLiesBetween() {
        List<ClipVertex> quad = List.of(new ClipVertex(0f, 0f, 0f, 0f, 0f, 0xFF000000),
                new ClipVertex(0f, 1f, 0f, 0f, 0f, 0xFF000000), new ClipVertex(1f, 1f, 0f, 0f, 0f, WHITE),
                new ClipVertex(1f, 0f, 0f, 0f, 0f, WHITE));

        List<ClipVertex> left = QuadRectClipper.clip(quad, new Rect(0f, 0f, 0.5f, 1f)).getFirst();

        ClipVertex cut = left.stream().filter(v -> v.x() > 0.25f).findFirst().orElseThrow();
        assertTrue((cut.color() & 0xFF) > 0 && (cut.color() & 0xFF) < 0xFF, Integer.toHexString(cut.color()));
    }

    /** A quad inside the rectangle passes as its own four vertices; one outside or touching an edge, nothing. */
    @Test
    void quadInsidePassesWholeAndOutsideNothing() {
        List<ClipVertex> pixel = List.of(mapped(0.5f, 0.5f), mapped(0.5f, 0.5625f),
                mapped(0.5625f, 0.5625f), mapped(0.5625f, 0.5f));

        assertEquals(List.of(pixel), QuadRectClipper.clip(pixel, new Rect(0.5f, 0.5f, 0.75f, 0.75f)));
        assertEquals(List.of(), QuadRectClipper.clip(pixel, new Rect(0f, 0f, 0.25f, 0.25f)));
        assertEquals(List.of(), QuadRectClipper.clip(pixel, new Rect(0.25f, 0.5f, 0.5f, 0.75f)));
    }
}
