package com.mercuriusxeno.goo.ability.program;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The share of a slick cell one held tick builds is the channeler's speed
 * factor over the slick step's ticks.
 * slick-resolves-faster-by-speed-over-unadjusted-speed
 */
class SlickStepTest {

    private static final int TICKS = 20;
    private static final double UNADJUSTED = 0.2;
    private static final SlickStep SLICK = new SlickStep(TICKS, 1.03, 1.08, 40, 1.0, 0.3);

    @Test
    void aChannelerOnSlickAtTwiceTheirUnadjustedSpeedBuildsTwiceTheShareATick() {
        ChannelHost host = mock(ChannelHost.class);
        when(host.channelerTravelSpeed()).thenReturn(2 * UNADJUSTED);
        when(host.channelerUnadjustedSpeed()).thenReturn(UNADJUSTED);
        when(host.channelerStandsOnSlick()).thenReturn(true);

        assertEquals(2f / TICKS, SLICK.layRate(host), 1e-6f);
    }
}
