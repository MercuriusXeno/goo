package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.client.ber.ItemTileClipper.ClipVertex;
import com.mercuriusxeno.goo.client.ber.ItemTileClipper.TileGrid;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cutting an item's quads into the tiles of its image: the tiles of one quad cover it
 * exactly with UVs carried along every cut, and a quad inside one tile passes whole
 * (decision tiles-of-the-items-image).
 */
class ItemTileClipperTest {

    private static final float EPSILON = 1e-5f;
    private static final int GRID = 4;
    private static final TileGrid UNIT_GRID = new TileGrid(0f, 0f, 1f, GRID);
    private static final float FRONT_Z = 8.5f / 16f;
    private static final float U0 = 0.25f;
    private static final float U_PER_X = 0.0625f;
    private static final float V0 = 0.5f;
    private static final float V_PER_Y = -0.0625f;
    private static final int WHITE = 0xFFFFFFFF;

    /** A vertex whose UV is the linear map of its position a sprite on the item atlas has. */
    private static ClipVertex mapped(float x, float y, float z) {
        return new ClipVertex(x, y, z, U0 + U_PER_X * x, V0 + V_PER_Y * y, WHITE);
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

    private static List<List<ClipVertex>> clipIntoEveryTile(List<ClipVertex> quad, TileGrid grid) {
        List<List<ClipVertex>> pieces = new ArrayList<>();
        for (int i = 0; i < grid.count() * grid.count(); i++) {
            pieces.addAll(ItemTileClipper.clip(quad, grid.tile(i)));
        }
        return pieces;
    }

    @Nested
    class Coverage {

        /**
         * A full face cut into the grid: every tile's vertices lie in that tile, the tiles'
         * areas sum to the face's, and every cut vertex's UV is the face's linear map at it.
         */
        @Test
        void tilesOfAFullFaceCoverItExactly() {
            List<ClipVertex> face = List.of(mapped(0f, 0f, FRONT_Z), mapped(0f, 1f, FRONT_Z),
                    mapped(1f, 1f, FRONT_Z), mapped(1f, 0f, FRONT_Z));
            float area = 0f;
            for (int i = 0; i < GRID * GRID; i++) {
                ItemTileClipper.Tile tile = UNIT_GRID.tile(i);
                List<List<ClipVertex>> pieces = ItemTileClipper.clip(face, tile);
                assertEquals(1, pieces.size(), "tile " + i);
                for (ClipVertex vertex : pieces.getFirst()) {
                    assertTrue(vertex.x() >= tile.minX() - EPSILON && vertex.x() <= tile.maxX() + EPSILON);
                    assertTrue(vertex.y() >= tile.minY() - EPSILON && vertex.y() <= tile.maxY() + EPSILON);
                    assertEquals(U0 + U_PER_X * vertex.x(), vertex.u(), EPSILON);
                    assertEquals(V0 + V_PER_Y * vertex.y(), vertex.v(), EPSILON);
                    assertEquals(FRONT_Z, vertex.z(), EPSILON);
                }
                area += areaXY(pieces.getFirst());
            }
            assertEquals(1f, area, EPSILON);
        }

        /**
         * A diamond cuts into pieces of three to six corners, fanned into quads that still
         * sum to the diamond's area, with UVs linear in position.
         */
        @Test
        void slantedEdgesFanIntoQuadsThatCoverTheQuad() {
            List<ClipVertex> diamond = List.of(mapped(0.5f, 0f, FRONT_Z), mapped(1f, 0.5f, FRONT_Z),
                    mapped(0.5f, 1f, FRONT_Z), mapped(0f, 0.5f, FRONT_Z));
            TileGrid fifths = new TileGrid(0f, 0f, 1f, 5);

            List<List<ClipVertex>> pieces = clipIntoEveryTile(diamond, fifths);

            float area = 0f;
            for (List<ClipVertex> piece : pieces) {
                assertEquals(4, piece.size());
                for (ClipVertex vertex : piece) {
                    assertEquals(U0 + U_PER_X * vertex.x(), vertex.u(), EPSILON);
                    assertEquals(V0 + V_PER_Y * vertex.y(), vertex.v(), EPSILON);
                }
                area += areaXY(piece);
            }
            assertEquals(0.5f, area, EPSILON);
            assertEquals(2, ItemTileClipper.clip(diamond, fifths.tile(fifths.count() + 1)).size(),
                    "the pentagon the diamond's edge cuts from tile (1, 1) fans into two quads");
        }

        /** A cut vertex's color blends the two corners it lies between. */
        @Test
        void cutVertexBlendsTheColorsItLiesBetween() {
            List<ClipVertex> quad = List.of(new ClipVertex(0f, 0f, 0f, 0f, 0f, 0xFF000000),
                    new ClipVertex(0f, 1f, 0f, 0f, 0f, 0xFF000000), new ClipVertex(1f, 1f, 0f, 0f, 0f, WHITE),
                    new ClipVertex(1f, 0f, 0f, 0f, 0f, WHITE));

            List<ClipVertex> left = ItemTileClipper.clip(quad, new TileGrid(0f, 0f, 1f, 2).tile(0)).getFirst();

            ClipVertex cut = left.stream().filter(v -> v.x() > 0.25f).findFirst().orElseThrow();
            assertTrue((cut.color() & 0xFF) > 0 && (cut.color() & 0xFF) < 0xFF, Integer.toHexString(cut.color()));
        }

        /** The grid is a square as wide as the model's larger side, centered on the model. */
        @Test
        void gridSquaresAroundTheModel() {
            TileGrid grid = TileGrid.around(new AABB(0.25, 0, 0.47, 0.75, 1, 0.53), GRID);

            assertEquals(0f, grid.minX(), EPSILON);
            assertEquals(0f, grid.minY(), EPSILON);
            assertEquals(1f, grid.span(), EPSILON);
            assertEquals(0.25f, grid.cell(), EPSILON);
        }
    }

    @Nested
    class EdgeQuads {

        /** An item's side face, flat in X, spanning a strip of Y and the item's thickness. */
        private static List<ClipVertex> sideFace(float x, float lowY, float highY) {
            return List.of(mapped(x, lowY, 0.47f), mapped(x, lowY, 0.53f),
                    mapped(x, highY, 0.53f), mapped(x, highY, 0.47f));
        }

        /** A side face inside one tile passes whole; against a tile it lies outside of, nothing. */
        @Test
        void edgeQuadInsideOneTilePassesWhole() {
            List<ClipVertex> side = sideFace(0.3125f, 0.3125f, 0.375f);

            assertEquals(List.of(side), ItemTileClipper.clip(side, UNIT_GRID.tile(GRID + 1)));
            assertEquals(List.of(), ItemTileClipper.clip(side, UNIT_GRID.tile(0)));
        }

        /** A face quad inside one tile passes as its own four vertices, in their order. */
        @Test
        void faceQuadInsideOneTilePassesWhole() {
            List<ClipVertex> pixel = List.of(mapped(0.5f, 0.5f, FRONT_Z), mapped(0.5f, 0.5625f, FRONT_Z),
                    mapped(0.5625f, 0.5625f, FRONT_Z), mapped(0.5625f, 0.5f, FRONT_Z));

            assertEquals(List.of(pixel), ItemTileClipper.clip(pixel, UNIT_GRID.tile(2 * GRID + 2)));
        }

        /** A side face lying on the edge two tiles share draws in one of them, not both. */
        @Test
        void edgeQuadOnASharedTileEdgeDrawsOnce() {
            assertEquals(1, clipIntoEveryTile(sideFace(0.25f, 0.3125f, 0.375f), UNIT_GRID).size());
            assertEquals(1, clipIntoEveryTile(sideFace(1f, 0.3125f, 0.375f), UNIT_GRID).size());
        }

        /** A side face spanning two rows cuts at the row edge into one piece per row. */
        @Test
        void edgeQuadAcrossTwoRowsCutsInTwo() {
            assertEquals(2, clipIntoEveryTile(sideFace(0.3125f, 0.125f, 0.375f), UNIT_GRID).size());
        }
    }
}
