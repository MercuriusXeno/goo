package com.mercuriusxeno.goo.client.radial;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** An active petal pulses between the resting and hovered opacity on game time, and an inactive one rests (decision wheel-pulses-the-active-effect). */
class PetalPulseTest {

    private static final float QUARTER_PERIOD = PetalPulse.PERIOD_TICKS / 4f;

    @Test
    void anActivePetalSwingsFromRestingToHovered() {
        assertEquals(RadialWheelRenderer.NORMAL_ALPHA, PetalPulse.alpha(true, 0f));
        assertEquals(RadialWheelRenderer.HOVER_ALPHA, PetalPulse.alpha(true, QUARTER_PERIOD));
    }

    @Test
    void anInactivePetalRests() {
        assertEquals(RadialWheelRenderer.NORMAL_ALPHA, PetalPulse.alpha(false, 0f));
        assertEquals(RadialWheelRenderer.NORMAL_ALPHA, PetalPulse.alpha(false, QUARTER_PERIOD));
    }
}
