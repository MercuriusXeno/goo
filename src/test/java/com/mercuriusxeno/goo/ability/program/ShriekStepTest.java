package com.mercuriusxeno.goo.ability.program;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A shriek's ring leaves the blob at radius zero and reaches its reach as
 * the period ends, growing evenly between
 * (decision shriek-is-a-sculk-call-that-draws-every-mob-it-reaches).
 */
class ShriekStepTest {

    private static final int PERIOD = 40;
    private static final double REACH = 16;

    @Test
    void ringLeavesTheBlobAtZero() {
        assertEquals(0, ShriekStep.ringRadius(0, PERIOD, REACH));
    }

    @Test
    void ringGrowsEvenlyToTheReach() {
        assertEquals(4, ShriekStep.ringRadius(10, PERIOD, REACH));
        assertEquals(REACH, ShriekStep.ringRadius(PERIOD, PERIOD, REACH));
    }
}
