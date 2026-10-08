package com.mercuriusxeno.goo.ability.program;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The soup ball hovers before the player, grows with its goo, moves closer
 * when something stands in its way and compresses once it can come no closer
 * (decision unmake-waves-dissolve-by-crucible-cost).
 */
class SoupBallTest {

    private static final double DELTA = 1e-9;

    @Test
    void withRoomItRidesAtItsDistanceRound() {
        SoupBall.Along along = SoupBall.along(SoupBall.DISTANCE + 0.5, 0.3);

        assertEquals(SoupBall.DISTANCE, along.distance(), DELTA);
        assertEquals(1, along.depth(), DELTA);
    }

    @Test
    void somethingInTheWayDrawsItCloserStillRound() {
        SoupBall.Along along = SoupBall.along(1.6, 0.3);

        assertEquals(1.3, along.distance(), DELTA);
        assertEquals(1, along.depth(), DELTA);
    }

    @Test
    void atItsClosestItCompressesIntoWhatItTouches() {
        SoupBall.Along along = SoupBall.along(SoupBall.CLOSEST + 0.15, 0.3);

        assertEquals(SoupBall.CLOSEST, along.distance(), DELTA);
        assertEquals(0.5, along.depth(), DELTA);
    }

    @Test
    void itCompressesNoFlatterThanItsFlattest() {
        assertEquals(SoupBall.FLATTEST, SoupBall.along(0, 0.3).depth(), DELTA);
    }

    @Test
    void itGrowsWithItsGooTowardABlockAcross() {
        assertEquals(SoupBall.MIN_RADIUS, SoupBall.radius(0), DELTA);
        assertTrue(SoupBall.radius(10_000) > SoupBall.radius(1_000));
        assertTrue(SoupBall.radius(Long.MAX_VALUE / 2) <= SoupBall.MAX_RADIUS);
    }
}
