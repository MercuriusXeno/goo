package com.mercuriusxeno.goo.client.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The goo cuboid's size by its volume: the fleck size at 1 mB, the capped
 * size at the cap volume and above, and the same step for each order of
 * magnitude between them (decision fleck-size-follows-volume-on-a-log-scale).
 */
class GooCuboidSizeTest {

    private static final float TOLERANCE = 1e-5f;

    @Test
    void oneMillibucketIsAFleck() {
        assertEquals(GooCuboidSize.FLECK_SIZE, GooCuboidSize.sizeOf(1), TOLERANCE);
    }

    @Test
    void theCapVolumeAndEveryVolumeAboveItStandAtTheCappedSize() {
        assertEquals(GooCuboidSize.CAPPED_SIZE, GooCuboidSize.sizeOf(GooCuboidSize.CAP_VOLUME), TOLERANCE);
        assertEquals(GooCuboidSize.CAPPED_SIZE, GooCuboidSize.sizeOf(Integer.MAX_VALUE), TOLERANCE);
    }

    @Test
    void eachOrderOfMagnitudeAddsTheSameStep() {
        float tenToHundred = GooCuboidSize.sizeOf(100) - GooCuboidSize.sizeOf(10);
        float hundredToThousand = GooCuboidSize.sizeOf(1_000) - GooCuboidSize.sizeOf(100);

        assertEquals(tenToHundred, hundredToThousand, TOLERANCE);
        assertEquals(true, tenToHundred > 0f, "a larger volume should draw a larger cuboid");
    }

    @Test
    void aPartUnderOneMillibucketDrawsAsAFleck() {
        assertEquals(GooCuboidSize.FLECK_SIZE, GooCuboidSize.sizeOf(0.25), TOLERANCE);
    }
}
