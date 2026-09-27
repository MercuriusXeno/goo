package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.block.crucible.CrucibleBasin;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mercuriusxeno.goo.client.SurfaceAgitation;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where the crucible lays its melting items: the head's tiles whole at the center of the
 * fill's top, breaking off in turn to rest across the open basin, above every ripple crest,
 * the waiting items in its corners (decisions dissolve-shader-on-item,
 * tiles-break-off-as-dissolve-advances).
 */
class CrucibleItemLayoutTest {

    private static final float EPSILON = 1e-6f;
    private static final long MELTED = 16_000;
    private static final float RESTING = RenderContext.RESTING_RIPPLE_AMPLITUDE;
    private static final float FULLY_AGITATED = RESTING + SurfaceAgitation.AGITATION_CEILING;
    private static final float CENTER = (CrucibleBasin.FOOTPRINT_MIN + CrucibleBasin.FOOTPRINT_MAX) / 2f;
    private static final float MID_DISSOLVE = 0.5f;

    private static void assertInsideBasin(CrucibleItemLayout.ItemPlacement placement) {
        float half = placement.size() / 2f;
        assertTrue(placement.x() - half >= CrucibleBasin.FOOTPRINT_MIN, placement + " leaves the basin at low X");
        assertTrue(placement.x() + half <= CrucibleBasin.FOOTPRINT_MAX, placement + " leaves the basin at high X");
        assertTrue(placement.z() - half >= CrucibleBasin.FOOTPRINT_MIN, placement + " leaves the basin at low Z");
        assertTrue(placement.z() + half <= CrucibleBasin.FOOTPRINT_MAX, placement + " leaves the basin at high Z");
    }

    /** Asserts a placement's square shares no area with any of the four corner slots' squares. */
    private static void assertClearOfCorners(CrucibleItemLayout.ItemPlacement placement) {
        for (CrucibleItemLayout.ItemPlacement corner
                : CrucibleItemLayout.waiting(null, RESTING, CrucibleItemLayout.WAITING_SLOTS)) {
            float reach = (placement.size() + corner.size()) / 2f;
            boolean apart = Math.abs(placement.x() - corner.x()) >= reach
                    || Math.abs(placement.z() - corner.z()) >= reach;
            assertTrue(apart, placement + " overlaps the corner slot " + corner);
        }
    }

    /** The spot a tile at a grid index draws at while the item is whole, rows running toward -Z. */
    private static float[] gridSpot(int index) {
        float step = CrucibleItemLayout.HEAD_SIZE / CrucibleItemLayout.TILE_GRID;
        float offset = (CrucibleItemLayout.TILE_GRID - 1) / 2f;
        return new float[] {CENTER + (index % CrucibleItemLayout.TILE_GRID - offset) * step,
            CENTER - (index / CrucibleItemLayout.TILE_GRID - offset) * step};
    }

    @Nested
    class Scatter {

        /** At fraction zero every tile sits at its grid spot, the tiles' union the head's square at the center. */
        @Test
        void wholeAtFractionZero() {
            CrucibleBasin.DrawnSurface surface = CrucibleBasin.drawnSurface(new CrucibleBasin.Volumes(MELTED, 0));
            List<CrucibleItemLayout.ItemPlacement> tiles = CrucibleItemLayout.headTiles(surface, RESTING, 0f);
            float half = CrucibleItemLayout.HEAD_SIZE / 2f;

            assertEquals(CrucibleItemLayout.TILE_COUNT, tiles.size());
            float minX = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE;
            float minZ = Float.MAX_VALUE;
            float maxZ = -Float.MAX_VALUE;
            for (int i = 0; i < tiles.size(); i++) {
                CrucibleItemLayout.ItemPlacement tile = tiles.get(i);
                assertEquals(gridSpot(i)[0], tile.x(), EPSILON, "tile " + i);
                assertEquals(gridSpot(i)[1], tile.z(), EPSILON, "tile " + i);
                assertEquals(surface.surfaceY() + RESTING + CrucibleItemLayout.FLOAT_LIFT, tile.y(), EPSILON);
                minX = Math.min(minX, tile.x() - tile.size() / 2f);
                maxX = Math.max(maxX, tile.x() + tile.size() / 2f);
                minZ = Math.min(minZ, tile.z() - tile.size() / 2f);
                maxZ = Math.max(maxZ, tile.z() + tile.size() / 2f);
            }
            assertEquals(CENTER - half, minX, EPSILON);
            assertEquals(CENTER + half, maxX, EPSILON);
            assertEquals(CENTER - half, minZ, EPSILON);
            assertEquals(CENTER + half, maxZ, EPSILON);
        }

        /** At fraction one every tile rests at its own spot inside the basin, clear of the corner slots. */
        @Test
        void scatteredAtFractionOne() {
            List<CrucibleItemLayout.ItemPlacement> tiles = CrucibleItemLayout.headTiles(null, RESTING, 1f);

            for (int i = 0; i < tiles.size(); i++) {
                assertInsideBasin(tiles.get(i));
                assertClearOfCorners(tiles.get(i));
                assertNotEquals(gridSpot(i)[0] + "," + gridSpot(i)[1], tiles.get(i).x() + "," + tiles.get(i).z());
            }
            assertEquals(tiles.size(), tiles.stream().distinct().count(), "two tiles share a resting spot");
        }

        /**
         * Midway, some tiles still sit in the grid and some have moved, each moved tile on
         * the segment from its grid spot to its resting spot.
         */
        @Test
        void tilesBreakOffInTurn() {
            List<CrucibleItemLayout.ItemPlacement> mid = CrucibleItemLayout.headTiles(null, RESTING, MID_DISSOLVE);
            List<CrucibleItemLayout.ItemPlacement> rest = CrucibleItemLayout.headTiles(null, RESTING, 1f);

            int unmoved = 0;
            int moved = 0;
            for (int i = 0; i < mid.size(); i++) {
                float[] grid = gridSpot(i);
                float dx = mid.get(i).x() - grid[0];
                float dz = mid.get(i).z() - grid[1];
                if (Math.abs(dx) < EPSILON && Math.abs(dz) < EPSILON) {
                    unmoved++;
                    continue;
                }
                moved++;
                float spanX = rest.get(i).x() - grid[0];
                float spanZ = rest.get(i).z() - grid[1];
                assertEquals(0f, dx * spanZ - dz * spanX, EPSILON, "tile " + i + " leaves its drift line");
                float along = (dx * spanX + dz * spanZ) / (spanX * spanX + spanZ * spanZ);
                assertTrue(along > 0f && along <= 1f + EPSILON, "tile " + i + " passes its ends: " + along);
            }
            assertTrue(unmoved > 0, "every tile has broken off midway");
            assertTrue(moved > 0, "no tile has broken off midway");
        }

        /** The rim tiles break off before the inner ones. */
        @Test
        void rimTilesBreakOffFirst() {
            int firstInner = CrucibleItemLayout.TILE_GRID + 1;
            float justPastRim = 12f / CrucibleItemLayout.TILE_COUNT * (1f - CrucibleItemLayout.DRIFT_SPAN);
            List<CrucibleItemLayout.ItemPlacement> tiles = CrucibleItemLayout.headTiles(null, RESTING, justPastRim);

            assertEquals(gridSpot(firstInner)[0], tiles.get(firstInner).x(), EPSILON);
            assertNotEquals(gridSpot(0)[0], tiles.get(0).x(), EPSILON);
        }
    }

    /** Every item rests above the highest crest a fully agitated ripple lifts the surface to. */
    @Test
    void itemsRestAboveTheHighestRippleCrest() {
        CrucibleBasin.DrawnSurface surface = CrucibleBasin.drawnSurface(new CrucibleBasin.Volumes(MELTED, 0));
        float crest = surface.surfaceY() + FULLY_AGITATED;

        for (CrucibleItemLayout.ItemPlacement tile : CrucibleItemLayout.headTiles(surface, FULLY_AGITATED, 0f)) {
            assertTrue(tile.y() > crest);
        }
        for (CrucibleItemLayout.ItemPlacement placement : CrucibleItemLayout.waiting(surface, FULLY_AGITATED, 3)) {
            assertTrue(placement.y() > crest);
        }
    }

    /** With nothing melted the head rests on the basin floor. */
    @Test
    void headRestsOnTheFloorBeforeAnythingMelts() {
        assertEquals(CrucibleBasin.FLOOR_Y + CrucibleItemLayout.FLOAT_LIFT,
                CrucibleItemLayout.headTiles(null, RESTING, 0f).getFirst().y(), EPSILON);
    }

    /** Three waiting stacks lie in three distinct corners inside the basin, above the fill. */
    @Test
    void threeWaitingItemsLieInsideTheBasin() {
        CrucibleBasin.DrawnSurface surface = CrucibleBasin.drawnSurface(new CrucibleBasin.Volumes(MELTED, 0));
        List<CrucibleItemLayout.ItemPlacement> waiting = CrucibleItemLayout.waiting(surface, RESTING, 3);

        assertEquals(3, waiting.size());
        for (CrucibleItemLayout.ItemPlacement placement : waiting) {
            assertInsideBasin(placement);
            assertTrue(placement.y() > surface.surfaceY());
        }
        assertNotEquals(waiting.get(0), waiting.get(1));
        assertNotEquals(waiting.get(1), waiting.get(2));
        assertNotEquals(waiting.get(0), waiting.get(2));
    }

    /** More stacks waiting than the basin has corners show one per corner. */
    @Test
    void waitingItemsCapAtTheCorners() {
        assertEquals(CrucibleItemLayout.WAITING_SLOTS, CrucibleItemLayout.waiting(null, RESTING, 9).size());
    }
}
