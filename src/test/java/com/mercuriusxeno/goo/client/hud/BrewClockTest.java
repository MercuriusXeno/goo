package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.ability.held.HeldEffects;
import org.junit.jupiter.api.Test;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A prepaid brew's clock reads minutes and seconds left, warns inside its last thirty seconds, and a glove effect with no expiry shows no clock (decision brew-runs-the-crawl-prepaid-on-a-shown-clock). */
class BrewClockTest {

    private static final long NOW = 5_000L;
    private static final float DELTA = 1e-6f;

    @Test
    void anHourOutReadsMinutesAndSecondsWithNoWarning() {
        assertEquals(Optional.of("3:03"), BrewClock.remaining(NOW + 3_661L, NOW));
        assertFalse(BrewClock.warns(NOW + 3_661L, NOW));
    }

    @Test
    void theLastThirtySecondsWarn() {
        assertEquals(Optional.of("0:20"), BrewClock.remaining(NOW + 400L, NOW));
        assertTrue(BrewClock.warns(NOW + 400L, NOW));
        assertTrue(BrewClock.warns(NOW + BrewClock.WARNING_TICKS, NOW));
        assertFalse(BrewClock.warns(NOW + BrewClock.WARNING_TICKS + 1, NOW));
    }

    @Test
    void aGloveEffectShowsNoClock() {
        assertEquals(Optional.empty(), BrewClock.remaining(HeldEffects.NEVER_EXPIRES, NOW));
        assertFalse(BrewClock.warns(HeldEffects.NEVER_EXPIRES, NOW));
    }

    @Test
    void onlyAWarningPulses() {
        assertEquals(1f, BrewClock.pulseAlpha(false, 7.5f), DELTA);
        assertTrue(BrewClock.pulseAlpha(true, 7.5f) < 1f);
    }
}
