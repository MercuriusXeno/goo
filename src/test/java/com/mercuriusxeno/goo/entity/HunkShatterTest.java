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
 * A struck hunk crumbles whole, every block at the moment it strikes
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
    void everyBlockCrumblesTheMomentTheHunkStrikes() {
        long struck = 1000;
        for (int turn = 0; turn <= 2; turn++) {
            assertEquals(struck, HunkShatter.crumbleStart(struck, turn), "turn " + turn + " starts late");
            assertEquals(struck + BlockHunk.CRUMBLE_TICKS, HunkShatter.crumbleEnd(struck, turn));
        }
    }

    @Test
    void aHunkStrikesWithTheFaceNearestItsMotion() {
        assertEquals(Direction.EAST, HunkShatter.leadingFace(new Vec3(1.8, -0.3, 0.1)));
        assertEquals(Direction.DOWN, HunkShatter.leadingFace(Vec3.ZERO));
    }
}
