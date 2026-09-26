package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.block.crucible.CrucibleBasin;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where the crucible lays its melting items: the head flat on the fill's top inside
 * the basin, the waiting items in its corners (decision dissolve-shader-on-item).
 */
class CrucibleItemLayoutTest {

    private static final float EPSILON = 1e-6f;
    private static final long MELTED = 16_000;

    private static void assertInsideBasin(CrucibleItemLayout.ItemPlacement placement) {
        float half = placement.size() / 2f;
        assertTrue(placement.x() - half >= CrucibleBasin.FOOTPRINT_MIN, placement + " leaves the basin at low X");
        assertTrue(placement.x() + half <= CrucibleBasin.FOOTPRINT_MAX, placement + " leaves the basin at high X");
        assertTrue(placement.z() - half >= CrucibleBasin.FOOTPRINT_MIN, placement + " leaves the basin at low Z");
        assertTrue(placement.z() + half <= CrucibleBasin.FOOTPRINT_MAX, placement + " leaves the basin at high Z");
    }

    /** The head lies at the fill's height, just above it, centered and inside the footprint. */
    @Test
    void headLiesOnTheFillInsideTheFootprint() {
        CrucibleBasin.DrawnSurface surface = CrucibleBasin.drawnSurface(new CrucibleBasin.Volumes(MELTED, 0));
        CrucibleItemLayout.ItemPlacement head = CrucibleItemLayout.head(surface);

        assertEquals(surface.surfaceY() + CrucibleItemLayout.FLOAT_LIFT, head.y(), EPSILON);
        assertEquals(CrucibleItemLayout.HEAD_SIZE, head.size(), EPSILON);
        assertInsideBasin(head);
    }

    /** With nothing melted the head rests on the basin floor. */
    @Test
    void headRestsOnTheFloorBeforeAnythingMelts() {
        assertEquals(CrucibleBasin.FLOOR_Y + CrucibleItemLayout.FLOAT_LIFT,
                CrucibleItemLayout.head(null).y(), EPSILON);
    }

    /** Three waiting stacks lie in three distinct corners inside the basin, above the fill. */
    @Test
    void threeWaitingItemsLieInsideTheBasin() {
        CrucibleBasin.DrawnSurface surface = CrucibleBasin.drawnSurface(new CrucibleBasin.Volumes(MELTED, 0));
        List<CrucibleItemLayout.ItemPlacement> waiting = CrucibleItemLayout.waiting(surface, 3);

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
        assertEquals(CrucibleItemLayout.WAITING_SLOTS, CrucibleItemLayout.waiting(null, 9).size());
    }
}
