package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.ability.held.HeldEffects;
import java.util.Locale;
import java.util.Optional;

/**
 * The clock a prepaid brew effect shows: its remaining time as minutes and
 * seconds, and a warning inside its last thirty seconds, so the brew never
 * wears off without warning. A glove effect has no expiry and shows no clock.
 * brew-runs-the-crawl-prepaid-on-a-shown-clock
 */
final class BrewClock {

    private static final int TICKS_PER_SECOND = 20;
    private static final int SECONDS_PER_MINUTE = 60;
    /** The ticks before the end the warning starts at: thirty seconds. */
    static final long WARNING_TICKS = 30L * TICKS_PER_SECOND;
    private static final String CLOCK_FORMAT = "%d:%02d";
    /** Opacity a warning never pulses below, and the rate it pulses at per gui tick. */
    private static final float DIMMEST = 0.35f;
    private static final float WHOLE = 1f;
    private static final float PULSE_RATE = 0.2f;

    private BrewClock() {
    }

    /**
     * The remaining time an effect shows.
     *
     * @param expiresAt the game time the effect ends at
     * @param now       the game time
     * @return minutes and seconds left, rounded down, or empty for an effect with no expiry
     */
    static Optional<String> remaining(long expiresAt, long now) {
        if (expiresAt == HeldEffects.NEVER_EXPIRES) {
            return Optional.empty();
        }
        long seconds = Math.max(0L, expiresAt - now) / TICKS_PER_SECOND;
        return Optional.of(String.format(Locale.ROOT, CLOCK_FORMAT, seconds / SECONDS_PER_MINUTE,
                seconds % SECONDS_PER_MINUTE));
    }

    /**
     * Answers whether the effect is inside its last thirty seconds.
     *
     * @param expiresAt the game time the effect ends at
     * @param now       the game time
     * @return true when the end is near, false for an effect with no expiry
     */
    static boolean warns(long expiresAt, long now) {
        return expiresAt != HeldEffects.NEVER_EXPIRES && expiresAt - now <= WARNING_TICKS;
    }

    /**
     * The opacity a warning pulses the clock and the overlay's sprites at:
     * whole when no warning stands, swinging between dim and whole when one does.
     *
     * @param warning whether the end is near
     * @param guiTime the gui time, fraction included
     * @return the opacity, zero to one
     */
    static float pulseAlpha(boolean warning, float guiTime) {
        if (!warning) {
            return WHOLE;
        }
        return DIMMEST + (WHOLE - DIMMEST) * Math.abs((float) Math.sin(guiTime * PULSE_RATE));
    }

}
