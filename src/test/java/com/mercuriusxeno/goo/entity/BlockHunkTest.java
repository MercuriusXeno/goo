package com.mercuriusxeno.goo.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A rising hunk blocks movement, so nothing falls through Spire's stone
 * mid-rise, while a lifted hunk collides with nothing
 * (decision spire-carries-its-riders-up-with-the-rising-stone), and a hunk's
 * block picks its texture's variant by its home cell, not the cell it moves
 * through (decision spire-entity-izes-its-blocks-and-raises-them-smoothly).
 */
class BlockHunkTest {

    @Test
    void aRisingHunkBlocksMovementAndALiftedOneDoesNot() {
        assertTrue(BlockHunk.blocksMovement(true));
        assertFalse(BlockHunk.blocksMovement(false));
    }

    // terrakinesis-release-drops-the-hunk-and-places-it-on-the-grid: a let-go hunk lands whole, a thrown one breaks
    @Test
    void aReleasedHunkAtRestIsPlacedAndAThrownOneStrikes() {
        assertEquals(BlockHunk.Landing.PLACE, BlockHunk.landingOf(false, false, true));
        assertEquals(BlockHunk.Landing.STRIKE, BlockHunk.landingOf(true, false, true));
        assertEquals(BlockHunk.Landing.STRIKE, BlockHunk.landingOf(true, true, false));
        assertEquals(BlockHunk.Landing.FLY, BlockHunk.landingOf(false, true, false));
        assertEquals(BlockHunk.Landing.FLY, BlockHunk.landingOf(true, false, false));
    }

    @Test
    void aBlocksTextureVariantSeedsFromItsHomeCellWhereverTheHunkCarriesIt() {
        BlockPos home = new BlockPos(10, 64, -3);
        BlockHunk.HunkBlock block = new BlockHunk.HunkBlock(new BlockPos(1, 2, -1), mock(BlockState.class));
        assertEquals(new BlockPos(11, 66, -4), BlockHunk.variantCellOf(home, block));
    }

    @Test
    void aSettledHunkFadesOutOverItsStoneFromTheMomentItSettles() {
        // A pillar settled at tick 100 draws faded over the world's stone, not as moving blocks over it.
        assertEquals(0, BlockHunk.lingerShare(100, 100), 1e-9);
        assertEquals(0.5, BlockHunk.lingerShare(100 + BlockHunk.SETTLE_LINGER_TICKS / 2.0, 100), 1e-9);
        assertEquals(1, BlockHunk.lingerShare(100 + BlockHunk.SETTLE_LINGER_TICKS, 100), 1e-9);
    }

    @Test
    void aHunkNotYetSettledDrawsNoFade() {
        assertEquals(BlockHunk.NOT_PLACED, BlockHunk.lingerShare(99.9, 100), 1e-9);
    }
}
