package com.mercuriusxeno.goo.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * A struck hunk crumbles block by block, the blocks leading into what it
 * struck first, each a set span after the last
 * (decision rock-grabs-hunks-of-rock-goo-blocks-as-a-huckable-entity).
 */
class HunkShatterTest {

    private static BlockHunk.HunkBlock at(int x, int y, int z) {
        return new BlockHunk.HunkBlock(new BlockPos(x, y, z), mock(BlockState.class));
    }

    @Test
    void theBlocksLeadingIntoTheStrikeCrumbleFirst() {
        List<BlockHunk.HunkBlock> column = List.of(at(0, 1, 0), at(0, 0, 0), at(0, -1, 0));
        assertArrayEquals(new int[] {2, 1, 0}, HunkShatter.turnsOf(column, Direction.DOWN));
        assertArrayEquals(new int[] {0, 1, 2}, HunkShatter.turnsOf(column, Direction.UP));
    }

    @Test
    void eachTurnBeginsASetSpanAfterTheLastAndEndsACrumbleLater() {
        long struck = 1000;
        assertEquals(struck, HunkShatter.crumbleStart(struck, 0));
        assertEquals(struck + 2L * BlockHunk.BLOCK_STAGGER_TICKS, HunkShatter.crumbleStart(struck, 2));
        assertEquals(struck + 2L * BlockHunk.BLOCK_STAGGER_TICKS + BlockHunk.CRUMBLE_TICKS,
                HunkShatter.crumbleEnd(struck, 2));
    }

    @Test
    void aHunkStrikesWithTheFaceNearestItsMotion() {
        assertEquals(Direction.EAST, HunkShatter.leadingFace(new Vec3(1.8, -0.3, 0.1)));
        assertEquals(Direction.DOWN, HunkShatter.leadingFace(Vec3.ZERO));
    }
}
