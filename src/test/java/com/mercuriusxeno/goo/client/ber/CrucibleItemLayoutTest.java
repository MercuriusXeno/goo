package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.block.crucible.CrucibleBasin;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mercuriusxeno.goo.client.SurfaceAgitation;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where the crucible lays its melting items: the head flat on the fill's top inside
 * the basin, above every ripple crest, the waiting items in its corners (decision
 * dissolve-shader-on-item).
 */
class CrucibleItemLayoutTest {

    private static final float EPSILON = 1e-6f;
    private static final long MELTED = 16_000;
    private static final float RESTING = RenderContext.RESTING_RIPPLE_AMPLITUDE;
    private static final float FULLY_AGITATED = RESTING + SurfaceAgitation.AGITATION_CEILING;

    private static void assertInsideBasin(CrucibleItemLayout.ItemPlacement placement) {
        float half = placement.size() / 2f;
        assertTrue(placement.x() - half >= CrucibleBasin.FOOTPRINT_MIN, placement + " leaves the basin at low X");
        assertTrue(placement.x() + half <= CrucibleBasin.FOOTPRINT_MAX, placement + " leaves the basin at high X");
        assertTrue(placement.z() - half >= CrucibleBasin.FOOTPRINT_MIN, placement + " leaves the basin at low Z");
        assertTrue(placement.z() + half <= CrucibleBasin.FOOTPRINT_MAX, placement + " leaves the basin at high Z");
    }

    /**
     * The head's tiles lie on the resting ripple's crest over the fill, their union the
     * head's square at the basin's center, inside the footprint.
     */
    @Test
    void headTilesTileTheHeadAtTheCenter() {
        CrucibleBasin.DrawnSurface surface = CrucibleBasin.drawnSurface(new CrucibleBasin.Volumes(MELTED, 0));
        List<CrucibleItemLayout.ItemPlacement> tiles = CrucibleItemLayout.headTiles(surface, RESTING);
        float center = (CrucibleBasin.FOOTPRINT_MIN + CrucibleBasin.FOOTPRINT_MAX) / 2f;
        float half = CrucibleItemLayout.HEAD_SIZE / 2f;

        assertEquals(CrucibleItemLayout.TILE_COUNT, tiles.size());
        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE;
        float maxZ = -Float.MAX_VALUE;
        float area = 0f;
        for (CrucibleItemLayout.ItemPlacement tile : tiles) {
            assertEquals(surface.surfaceY() + RESTING + CrucibleItemLayout.FLOAT_LIFT, tile.y(), EPSILON);
            assertInsideBasin(tile);
            minX = Math.min(minX, tile.x() - tile.size() / 2f);
            maxX = Math.max(maxX, tile.x() + tile.size() / 2f);
            minZ = Math.min(minZ, tile.z() - tile.size() / 2f);
            maxZ = Math.max(maxZ, tile.z() + tile.size() / 2f);
            area += tile.size() * tile.size();
        }
        assertEquals(center - half, minX, EPSILON);
        assertEquals(center + half, maxX, EPSILON);
        assertEquals(center - half, minZ, EPSILON);
        assertEquals(center + half, maxZ, EPSILON);
        assertEquals(CrucibleItemLayout.HEAD_SIZE * CrucibleItemLayout.HEAD_SIZE, area, EPSILON);
        assertEquals(tiles.size(), tiles.stream().distinct().count());
    }

    /** A tile's row runs along the model's Y, which the face-up item turns toward -Z. */
    @Test
    void laterRowsLieTowardNegativeZ() {
        List<CrucibleItemLayout.ItemPlacement> tiles = CrucibleItemLayout.headTiles(null, RESTING);

        assertTrue(tiles.get(1).x() > tiles.get(0).x());
        assertEquals(tiles.get(0).z(), tiles.get(1).z(), EPSILON);
        assertTrue(tiles.get(CrucibleItemLayout.TILE_GRID).z() < tiles.get(0).z());
        assertEquals(tiles.get(0).x(), tiles.get(CrucibleItemLayout.TILE_GRID).x(), EPSILON);
    }

    /** Every item rests above the highest crest a fully agitated ripple lifts the surface to. */
    @Test
    void itemsRestAboveTheHighestRippleCrest() {
        CrucibleBasin.DrawnSurface surface = CrucibleBasin.drawnSurface(new CrucibleBasin.Volumes(MELTED, 0));
        float crest = surface.surfaceY() + FULLY_AGITATED;

        for (CrucibleItemLayout.ItemPlacement tile : CrucibleItemLayout.headTiles(surface, FULLY_AGITATED)) {
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
                CrucibleItemLayout.headTiles(null, RESTING).getFirst().y(), EPSILON);
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
