package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * The shared gradual break progresses each block a step hands it by its rate,
 * cracks it by its share and breaks it once full, and clears a block no
 * longer handed to it (decision gradual-breaks-share-one-break-texture-piece).
 */
class GradualBreakTest {

    private static final BlockPos FAST = new BlockPos(1, 2, 0);
    private static final BlockPos SLOW = new BlockPos(2, 2, 0);
    /** Full on the fourth tick. */
    private static final double FAST_RATE = 0.25;
    private static final double SLOW_RATE = 0.1;
    private static final int TICKS_TO_FILL_FAST = 4;

    private final GradualBreak gradualBreak = new GradualBreak();
    private final BlockBreakHost host = mock(BlockBreakHost.class);
    /** The last crack stage the break reported for each block. */
    private final Map<BlockPos, Integer> stages = new HashMap<>();

    @BeforeEach
    void recordTheCracks() {
        doAnswer(call -> stages.put(call.getArgument(0), call.getArgument(1))).when(host)
                .crackBlock(any(), anyInt());
    }

    private void feedBoth() {
        Map<BlockPos, Double> rates = new LinkedHashMap<>();
        rates.put(FAST, FAST_RATE);
        rates.put(SLOW, SLOW_RATE);
        gradualBreak.progress(rates, host);
    }

    @Test
    void eachCellsCrackStageRisesWithItsProgressAndTheFasterCellsIsNeverLower() {
        int lastFast = GradualBreak.CLEARED;
        int lastSlow = GradualBreak.CLEARED;
        for (int tick = 1; tick < TICKS_TO_FILL_FAST; tick++) {
            feedBoth();
            int fast = stages.get(FAST);
            int slow = stages.get(SLOW);
            assertTrue(fast > lastFast, "tick " + tick + ": the fast cell's stage " + fast + " did not rise");
            assertTrue(slow >= lastSlow, "tick " + tick + ": the slow cell's stage " + slow + " fell");
            assertTrue(fast >= slow, "tick " + tick + ": the fast cell " + fast + " cracks less than " + slow);
            lastFast = fast;
            lastSlow = slow;
        }
        assertTrue(lastSlow > 0, "the slow cell never cracked past its first stage");
    }

    @Test
    void theCellBreaksOnTheTickItsProgressFillsAndItsCracksClear() {
        for (int tick = 1; tick < TICKS_TO_FILL_FAST; tick++) {
            feedBoth();
        }
        verify(host, never()).breakBlock(any());

        feedBoth();

        verify(host).breakBlock(FAST);
        verify(host, never()).breakBlock(SLOW);
        assertEquals(GradualBreak.CLEARED, stages.get(FAST));
    }

    @Test
    void releasingTheHoldClearsEveryCellItWasBreaking() {
        feedBoth();
        feedBoth();

        gradualBreak.release();

        assertEquals(GradualBreak.CLEARED, stages.get(FAST));
        assertEquals(GradualBreak.CLEARED, stages.get(SLOW));
    }

    @Test
    void aCellNoLongerFedReportsClearedAndStartsOverWhenFedAgain() {
        feedBoth();
        feedBoth();

        gradualBreak.progress(Map.of(FAST, FAST_RATE), host);
        assertEquals(GradualBreak.CLEARED, stages.get(SLOW));

        // Two ticks at 0.1 cracked it to stage 2; fed afresh it holds one tick's share, stage 1.
        gradualBreak.progress(Map.of(SLOW, SLOW_RATE), host);
        assertEquals(1, stages.get(SLOW));
    }
}
