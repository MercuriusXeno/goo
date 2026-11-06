package com.mercuriusxeno.goo.ability.program;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A convoke's ring leaves the blob at radius zero and reaches its reach as
 * the period ends, growing evenly between
 * (decision convoke-is-a-sculk-call-that-draws-every-mob-the-ring-touches).
 */
class ConvokeStepTest {

    private static final int PERIOD = 40;
    private static final double REACH = 16;

    @Test
    void ringLeavesTheBlobAtZero() {
        assertEquals(0, ConvokeStep.ringRadius(0, PERIOD, REACH));
    }

    @Test
    void ringGrowsEvenlyToTheReach() {
        assertEquals(4, ConvokeStep.ringRadius(10, PERIOD, REACH));
        assertEquals(REACH, ConvokeStep.ringRadius(PERIOD, PERIOD, REACH));
    }
}
