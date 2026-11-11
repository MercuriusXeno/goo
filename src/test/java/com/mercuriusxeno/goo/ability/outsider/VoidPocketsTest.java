package com.mercuriusxeno.goo.ability.outsider;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers a saved room from before the extent: a platform saved as a spiral
 * cell count loads as the extent of the square those cells completed, so no
 * player loses ground (decision outsider-room-grows-fractionally-on-all-sides-at-once).
 */
class VoidPocketsTest {

    private static final double EXACT = 1.0E-9;

    @ParameterizedTest(name = "{0} cells load as ring {1}")
    @CsvSource({"9, 1", "10, 1", "25, 2", "48, 2", "49, 3"})
    void aCellCountLoadsAsTheExtentOfTheSquareItCompleted(int cells, int completedRing) {
        JsonObject saved = new JsonObject();
        saved.addProperty("cells", cells);
        saved.addProperty("fed", 7L);

        VoidPockets.Platform loaded = VoidPockets.Platform.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();

        assertEquals(completedRing + 0.5, loaded.extent(), EXACT);
        for (int east = -completedRing; east <= completedRing; east++) {
            for (int south = -completedRing; south <= completedRing; south++) {
                assertTrue(RoomExtent.holdsWholeBlock(loaded.extent(), east, south),
                        "block (" + east + ", " + south + ") of the completed square should stand inside");
            }
        }
    }
}
