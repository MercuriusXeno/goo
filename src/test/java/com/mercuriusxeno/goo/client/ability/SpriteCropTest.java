package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A chunk wears its own cell of its block's particle sprite: the cells cut the
 * sprite's window of the atlas into an equal grid, the first at the sprite's
 * top left and the last at its bottom right
 * (decision unmake-peels-the-block-into-chunks-that-stream-to-the-hand).
 */
class SpriteCropTest {

    private static final float U0 = 0.25f;
    private static final float U1 = 0.3125f;
    private static final float V0 = 0.5f;
    private static final float V1 = 0.5625f;
    private static final int CELLS = 4;
    private static final float CELL = (U1 - U0) / CELLS;
    private static final float SLACK = 1e-6f;

    @Test
    void theCellsTileTheSpriteFromItsTopLeftToItsBottomRight() {
        SpriteCrop first = SpriteCrop.cellOf(U0, U1, V0, V1, 0, 0, CELLS);
        SpriteCrop last = SpriteCrop.cellOf(U0, U1, V0, V1, CELLS - 1, CELLS - 1, CELLS);
        assertEquals(U0, first.u0(), SLACK);
        assertEquals(V0, first.v0(), SLACK);
        assertEquals(U1, last.u1(), SLACK);
        assertEquals(V1, last.v1(), SLACK);
    }

    @Test
    void neighbouringCellsMeetEdgeToEdge() {
        SpriteCrop left = SpriteCrop.cellOf(U0, U1, V0, V1, 1, 2, CELLS);
        SpriteCrop right = SpriteCrop.cellOf(U0, U1, V0, V1, 2, 2, CELLS);
        SpriteCrop below = SpriteCrop.cellOf(U0, U1, V0, V1, 1, CELLS - 1, CELLS);
        assertEquals(left.u1(), right.u0(), SLACK);
        assertEquals(left.v1(), below.v0(), SLACK);
        assertEquals(CELL, left.u1() - left.u0(), SLACK);
    }
}
