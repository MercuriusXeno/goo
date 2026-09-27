package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.block.crucible.CrucibleBasin;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mercuriusxeno.goo.client.SurfaceAgitation;
import com.mercuriusxeno.goo.client.SurfaceRipple;
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
    /** Float rounding across a sum taken in another order, far under a texture pixel. */
    private static final float FLOAT_ROUNDING = 1e-5f;
    private static final long MELTED = 16_000;
    private static final float RESTING = RenderContext.RESTING_RIPPLE_AMPLITUDE;
    private static final float FULLY_AGITATED = RESTING + SurfaceAgitation.AGITATION_CEILING;
    private static final float CENTER = (CrucibleBasin.FOOTPRINT_MIN + CrucibleBasin.FOOTPRINT_MAX) / 2f;
    private static final float MID_DISSOLVE = 0.5f;
    /** The wave over a crucible at the world origin at the start of the day. */
    private static final SurfaceRipple.Field STILL = new SurfaceRipple.Field(0, 0, 0f);
    /** Half the primary wave's wavelength, in blocks. */
    private static final float HALF_PRIMARY_WAVELENGTH = (float) (Math.PI / SurfaceRipple.PRIMARY_WAVENUMBER);

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
            List<CrucibleItemLayout.ItemPlacement> tiles = CrucibleItemLayout.headTiles(surface, RESTING, 0f, STILL);
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
                assertEquals(CrucibleItemLayout.tileY(surface, RESTING, STILL, tile.x(), tile.z()), tile.y(), EPSILON);
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
            List<CrucibleItemLayout.ItemPlacement> tiles = CrucibleItemLayout.headTiles(null, RESTING, 1f, STILL);

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
            List<CrucibleItemLayout.ItemPlacement> mid = CrucibleItemLayout.headTiles(null, RESTING, MID_DISSOLVE, STILL);
            List<CrucibleItemLayout.ItemPlacement> rest = CrucibleItemLayout.headTiles(null, RESTING, 1f, STILL);

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
            List<CrucibleItemLayout.ItemPlacement> tiles = CrucibleItemLayout.headTiles(null, RESTING, justPastRim, STILL);

            assertEquals(gridSpot(firstInner)[0], tiles.get(firstInner).x(), EPSILON);
            float moved = Math.abs(gridSpot(0)[0] - tiles.get(0).x()) + Math.abs(gridSpot(0)[1] - tiles.get(0).z());
            assertTrue(moved > EPSILON, "the corner tile has not broken off");
        }
    }

    @Nested
    class Bobbing {

        /**
         * Each tile rests at the surface plus the amplitude times the wave at its own spot
         * plus the tile lift, at every fraction the tiles drift through.
         */
        @Test
        void tileRestsOnTheWaveAtItsSpot() {
            CrucibleBasin.DrawnSurface surface = CrucibleBasin.drawnSurface(new CrucibleBasin.Volumes(MELTED, 0));
            SurfaceRipple.Field field = new SurfaceRipple.Field(-37, 112, 0.3f);

            for (float fraction : new float[] {0f, MID_DISSOLVE, 1f}) {
                for (CrucibleItemLayout.ItemPlacement tile
                        : CrucibleItemLayout.headTiles(surface, FULLY_AGITATED, fraction, field)) {
                    float wave = SurfaceRipple.at(-37 + tile.x(), 112 + tile.z(), 0.3f);
                    assertEquals(surface.surfaceY() + FULLY_AGITATED * wave
                            + CrucibleItemLayout.tileLift(FULLY_AGITATED), tile.y(), FLOAT_ROUNDING);
                }
            }
        }

        /** A tile on the primary crest and one half a wavelength on, in its trough, ride the wave out of step. */
        @Test
        void tilesHalfAWavelengthApartDifferInHeight() {
            CrucibleBasin.DrawnSurface surface = CrucibleBasin.drawnSurface(new CrucibleBasin.Volumes(MELTED, 0));
            float crest = (float) (2.5 * Math.PI / (SurfaceRipple.PRIMARY_WAVENUMBER
                    * (SurfaceRipple.PRIMARY_DIRECTION_X + SurfaceRipple.PRIMARY_DIRECTION_Z)));
            float x = crest;
            float z = crest;
            float farX = x + SurfaceRipple.PRIMARY_DIRECTION_X * HALF_PRIMARY_WAVELENGTH;
            float farZ = z + SurfaceRipple.PRIMARY_DIRECTION_Z * HALF_PRIMARY_WAVELENGTH;

            float near = CrucibleItemLayout.tileY(surface, FULLY_AGITATED, STILL, x, z);
            float far = CrucibleItemLayout.tileY(surface, FULLY_AGITATED, STILL, farX, farZ);

            assertTrue(Math.abs(near - far) > FULLY_AGITATED / 4f, near + " vs " + far);
        }

        /** The tile lift grows with the amplitude and never drops below the float lift. */
        @Test
        void tileLiftCoversTheSagAcrossATile() {
            float halfDiagonal = CrucibleItemLayout.TILE_SIZE / 2f * (float) Math.sqrt(2.0);
            float sag = (float) (1.0 - Math.cos(SurfaceRipple.SECONDARY_WAVENUMBER * halfDiagonal));

            assertEquals(CrucibleItemLayout.FLOAT_LIFT, CrucibleItemLayout.tileLift(0f), EPSILON);
            assertEquals(FULLY_AGITATED * sag + CrucibleItemLayout.FLOAT_LIFT,
                    CrucibleItemLayout.tileLift(FULLY_AGITATED), EPSILON);
        }

        /** A waiting item keeps the crest rule: the fill, a full amplitude and the float lift. */
        @Test
        void waitingItemsRideTheCrest() {
            CrucibleBasin.DrawnSurface surface = CrucibleBasin.drawnSurface(new CrucibleBasin.Volumes(MELTED, 0));

            for (CrucibleItemLayout.ItemPlacement placement : CrucibleItemLayout.waiting(surface, FULLY_AGITATED, 3)) {
                assertEquals(surface.surfaceY() + FULLY_AGITATED + CrucibleItemLayout.FLOAT_LIFT, placement.y(),
                        EPSILON);
            }
        }
    }

    /** With nothing melted the head rests on the basin floor. */
    @Test
    void headRestsOnTheFloorBeforeAnythingMelts() {
        assertEquals(CrucibleBasin.FLOOR_Y + CrucibleItemLayout.FLOAT_LIFT,
                CrucibleItemLayout.headTiles(null, RESTING, 0f, STILL).getFirst().y(), EPSILON);
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
