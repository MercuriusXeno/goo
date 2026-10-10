package com.mercuriusxeno.goo.network;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * A hold paints each block once with the block standing there, and a block
 * it has stepped leaves the painted set and is never painted again in the
 * hold (decision decay-gnats-degrade-each-block-once).
 */
class HoldMarksTest {

    private static final BlockPos BLOCK = new BlockPos(1, 2, 3);
    private final Block stone = mock(Block.class);
    private final Block cobblestone = mock(Block.class);

    @Test
    void aBlockKeepsTheOriginItWasFirstPaintedWith() {
        HoldMarks marks = new HoldMarks();
        marks.paint(BLOCK, stone);
        marks.paint(BLOCK, cobblestone);

        assertEquals(Map.of(BLOCK, stone), marks.painted());
    }

    @Test
    void aSteppedBlockLeavesThePaintedSetAndIsNotPaintedAgain() {
        HoldMarks marks = new HoldMarks();
        marks.paint(BLOCK, stone);
        marks.noteStepped(BLOCK);
        marks.paint(BLOCK, cobblestone);

        assertTrue(marks.painted().isEmpty());
        assertTrue(marks.stepped(BLOCK));
    }

    @Test
    void anUnpaintedBlockCanBePaintedAgain() {
        HoldMarks marks = new HoldMarks();
        marks.paint(BLOCK, stone);
        marks.unpaint(BLOCK);
        marks.paint(BLOCK, cobblestone);

        assertEquals(Map.of(BLOCK, cobblestone), marks.painted());
    }
}
