package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The glass field shows the stone around the diver faintly and where two
 * different blocks meet a little brighter, one face to each place: a block
 * carrying on into the same block shows only its faces toward the diver;
 * against air a block shows its own face, seen from either side; where two
 * blocks meet, the farther block's face
 * (decision sculker-shows-blocks-translucent-close-up).
 */
class SculkerGlassRendererTest {

    private static final double NEAR = 2;
    private static final double FAR = 3;

    @Test
    void theSameBlockCarryingOnShowsOnlyItsFacesTowardTheDiver() {
        assertTrue(SculkerGlassRenderer.faceShows(false, true, true, FAR, NEAR));
        assertFalse(SculkerGlassRenderer.faceShows(false, true, false, FAR, NEAR));
    }

    @Test
    void aBlockShowsItsFaceAgainstAirFromEitherSide() {
        assertTrue(SculkerGlassRenderer.faceShows(true, false, false, NEAR, FAR));
        assertTrue(SculkerGlassRenderer.faceShows(true, false, true, NEAR, FAR));
    }

    @Test
    void whereTwoBlocksMeetTheFartherBlocksFaceShows() {
        assertTrue(SculkerGlassRenderer.faceShows(false, false, false, FAR, NEAR));
        assertFalse(SculkerGlassRenderer.faceShows(false, false, true, NEAR, FAR));
    }

    @Test
    void aMeetingShowsBrighterThanTheStoneCarryingOn() {
        assertTrue(SculkerGlassRenderer.faceOpacity(false) > SculkerGlassRenderer.faceOpacity(true));
    }
}
