package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bore's vortex widens with the bore's cone, about half a block at the reach
 * of a 15 degree cone (decision bore-breaks-a-15-degree-cone).
 */
class BoreVortexTest {

    private static final double CONE_DEGREES = 15;
    private static final double REACH = 4;
    /** 4 * tan(7.5 degrees). */
    private static final double RADIUS_AT_THE_REACH = 0.527;
    private static final double SLACK = 0.01;

    @Test
    void theRadiusFollowsTheConeOutToTheReach() {
        assertEquals(RADIUS_AT_THE_REACH, BoreVortex.radiusAt(REACH, CONE_DEGREES), SLACK);
    }

    @Test
    void theRadiusIsZeroAtTheEye() {
        assertEquals(0, BoreVortex.radiusAt(0, CONE_DEGREES));
    }
}
