package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers how long a mob Heal or Drain touched wears its life mark, a later
 * touch renewing it, and how the mark fades out after the last touch.
 */
class TouchedMobsTest {

    private static final int COW_ID = 7;
    private static final int PIG_ID = 8;
    private static final long TOUCHED_AT = 100;
    private static final long LAPSE_TICK = TOUCHED_AT + TouchedMobs.MARK_TICKS;
    /** About 4 seconds at 20 ticks a second. */
    private static final int FOUR_SECONDS_TICKS = 80;
    /** Drain's gap between trickles: a hit leaves 10 immunity ticks, and Drain strikes only a vulnerable mob. */
    private static final int DRAIN_TRICKLE_GAP = 10;
    private static final float WHOLE = 1f;
    private static final float TOLERANCE = 0.02f;

    @Test
    void touchedMobIsMarkedUntilItsLapse() {
        TouchedMobs touched = new TouchedMobs();
        touched.touch(COW_ID, TOUCHED_AT);
        assertTrue(touched.marked(COW_ID, LAPSE_TICK - 1));
        assertFalse(touched.marked(COW_ID, LAPSE_TICK));
    }

    @Test
    void laterTouchMovesTheLapseLater() {
        TouchedMobs touched = new TouchedMobs();
        touched.touch(COW_ID, TOUCHED_AT);
        touched.touch(COW_ID, TOUCHED_AT + 1);
        assertTrue(touched.marked(COW_ID, LAPSE_TICK));
    }

    @Test
    void touchMarksThatMobAlone() {
        TouchedMobs touched = new TouchedMobs();
        touched.touch(COW_ID, TOUCHED_AT);
        touched.touch(PIG_ID, TOUCHED_AT + TouchedMobs.MARK_TICKS);
        assertTrue(touched.marked(PIG_ID, LAPSE_TICK));
        assertFalse(touched.marked(COW_ID, LAPSE_TICK));
    }

    @Nested
    class Fade {

        @Test
        void markFadesToNothingOverFourSecondsAfterTheLastTouch() {
            TouchedMobs touched = new TouchedMobs();
            touched.touch(COW_ID, TOUCHED_AT);
            long fadeStart = LAPSE_TICK - FOUR_SECONDS_TICKS;
            assertEquals(WHOLE, touched.opacity(COW_ID, fadeStart), TOLERANCE, "whole as the fade starts");
            assertEquals(0.5f, touched.opacity(COW_ID, fadeStart + FOUR_SECONDS_TICKS / 2f), TOLERANCE,
                    "near half at the fade's midpoint");
            assertEquals(0f, touched.opacity(COW_ID, LAPSE_TICK), "nothing at the lapse");
        }

        @Test
        void markStaysWholeAcrossDrainsTrickleGap() {
            TouchedMobs touched = new TouchedMobs();
            for (long trickle = TOUCHED_AT; trickle < TOUCHED_AT + 5 * DRAIN_TRICKLE_GAP; trickle += DRAIN_TRICKLE_GAP) {
                touched.touch(COW_ID, trickle);
                float beforeNextTrickle = trickle + DRAIN_TRICKLE_GAP - 0.01f;
                assertEquals(WHOLE, touched.opacity(COW_ID, beforeNextTrickle), "whole until the next trickle lands");
            }
        }

        @Test
        void touchDuringTheFadeRestoresTheMark() {
            TouchedMobs touched = new TouchedMobs();
            touched.touch(COW_ID, TOUCHED_AT);
            long midFade = LAPSE_TICK - FOUR_SECONDS_TICKS / 2;
            assertTrue(touched.opacity(COW_ID, midFade) < WHOLE, "faded partway before the new touch");
            touched.touch(COW_ID, midFade);
            assertEquals(WHOLE, touched.opacity(COW_ID, midFade), "whole again on the new touch");
        }
    }
}
