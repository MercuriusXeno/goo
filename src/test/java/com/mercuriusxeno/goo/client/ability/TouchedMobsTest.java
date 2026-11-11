package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers how long a mob Heal or Drain touched wears its life mark, and a
 * later touch renewing it.
 */
class TouchedMobsTest {

    private static final int COW_ID = 7;
    private static final int PIG_ID = 8;
    private static final long TOUCHED_AT = 100;
    private static final long LAPSE_TICK = TOUCHED_AT + TouchedMobs.MARK_TICKS;

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
}
