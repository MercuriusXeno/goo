package com.mercuriusxeno.goo.lunar;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The fence round a feature's origin: it spans the origin's chunk and one chunk out each way, and two chunks out lies
 * outside it.
 * decision warren-sculk-homes-sensors-and-catalysts-and-grows-in-lunar-rock
 */
class WritableFenceTest {

    /** An origin in chunk 0, 0. */
    private static final BlockPos ORIGIN = new BlockPos(5, -40, 9);

    @Nested
    class Bounds {

        @Test
        void theFenceSpansTheOriginsChunkAndOneChunkOutEachWay() {
            assertEquals(-16, WritableFence.writableMin(ORIGIN.getX()));
            assertEquals(31, WritableFence.writableMax(ORIGIN.getX()));
            assertEquals(-32, WritableFence.writableMin(-1));
            assertEquals(15, WritableFence.writableMax(-1));
        }

        @Test
        void twoChunksOutLiesOutside() {
            assertTrue(WritableFence.inside(ORIGIN, new BlockPos(31, -40, -16)));
            assertFalse(WritableFence.inside(ORIGIN, new BlockPos(32, -40, 9)));
            assertFalse(WritableFence.inside(ORIGIN, new BlockPos(5, -40, -17)));
        }
    }

}
