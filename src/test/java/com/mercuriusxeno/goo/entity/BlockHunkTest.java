package com.mercuriusxeno.goo.entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A rising hunk blocks movement, so nothing falls through Spire's stone
 * mid-rise, while a lifted hunk collides with nothing
 * (decision spire-carries-its-riders-up-with-the-rising-stone).
 */
class BlockHunkTest {

    @Test
    void aRisingHunkBlocksMovementAndALiftedOneDoesNot() {
        assertTrue(BlockHunk.blocksMovement(true));
        assertFalse(BlockHunk.blocksMovement(false));
    }
}
