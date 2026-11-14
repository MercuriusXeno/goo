package com.mercuriusxeno.goo.throwing;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A stream throwing fewer drips a tick than its fan holds takes up the fan
 * where the last tick left off, the axis first, so a hold sprays the whole
 * cone; one throwing at least the fan throws all of it every tick
 * (decision goop-drains-per-cubic-meter-of-slime-placed, the spray's pace).
 */
class CoatRayTest {

    @Test
    void aHoldsFirstDripRunsDownTheAxis() {
        assertEquals(List.of(CoatRay.coneFan().getFirst()), CoatRay.sharesThrownOn(1, 1));
    }

    @Test
    void oneDripATickWalksTheWholeFanInAFansWorthOfTicks() {
        List<CoatRay.ConeShare> thrown = new ArrayList<>();
        for (int held = 1; held <= CoatRay.FAN_SHARES; held++) {
            thrown.addAll(CoatRay.sharesThrownOn(held, 1));
        }

        assertEquals(CoatRay.coneFan(), thrown);
    }

    /** Two drips a tick: the ninth tick starts on the fan's last share, the sixteenth of seventeen, and wraps. */
    @Test
    void aTicksDripsWrapPastTheFansEnd() {
        List<CoatRay.ConeShare> fan = CoatRay.coneFan();

        assertEquals(List.of(fan.getLast(), fan.getFirst()), CoatRay.sharesThrownOn(9, 2));
    }

    @Test
    void aPaceAtTheFansSizeThrowsTheWholeFanEveryTick() {
        assertEquals(CoatRay.coneFan(), CoatRay.sharesThrownOn(7, CoatRay.FAN_SHARES));
    }
}
