package com.mercuriusxeno.goo.client.fade;

import org.jspecify.annotations.Nullable;
import java.util.Optional;

/**
 * One drawn trace of a held effect, lingering after the effect ends: it
 * remembers the last standing state the trace drew from and the time that
 * state was first seen gone, and answers full strength while the state stands
 * and a strength falling to nothing over the second after. The server ends the
 * mechanics the tick the effect ends; the client alone eases the drawing, and
 * nothing fades before the end.
 * brew-effect-visuals-linger-and-fade-over-a-second-after-the-effect-ends
 *
 * @param <S> the state the trace draws from
 */
public final class Afterglow<S> {

    /** The game ticks a trace takes to fade out after its state is gone: about a second. */
    public static final int FADE_TICKS = 20;
    private static final int ALPHA_SHIFT = 24;
    private static final int RGB_MASK = 0xFFFFFF;
    private static final int OPAQUE_ALPHA = 0xFF;

    private @Nullable S remembered;
    private float goneSince = Float.NaN;

    /**
     * What a trace draws this frame.
     *
     * @param state    the state it draws from, standing or remembered
     * @param strength one while the state stands, falling to zero over the fade
     * @param stands   whether the state stands this frame
     * @param <S>      the state the trace draws from
     */
    public record Glow<S>(S state, float strength, boolean stands) {
    }

    /**
     * Reads the trace's state as the client sees it this frame.
     *
     * @param standing the state standing now, or null once it is gone
     * @param now      the game time, partial tick included
     * @return what the trace draws, or empty once nothing stands and the fade is spent
     */
    public Optional<Glow<S>> read(@Nullable S standing, float now) {
        if (standing != null) {
            remembered = standing;
            goneSince = Float.NaN;
            return Optional.of(new Glow<>(standing, 1f, true));
        }
        S last = remembered;
        if (last == null) {
            return Optional.empty();
        }
        if (Float.isNaN(goneSince)) {
            goneSince = now;
        }
        float elapsed = now - goneSince;
        if (elapsed < 0f || elapsed >= FADE_TICKS) {
            // a clock running backwards is a new world's, so the trace is spent there too
            remembered = null;
            goneSince = Float.NaN;
            return Optional.empty();
        }
        return Optional.of(new Glow<>(last, strength(goneSince, now), false));
    }

    /**
     * The strength a trace draws at a time after its state was first seen gone.
     *
     * @param goneSince the game time the state was first seen gone
     * @param now       the game time, partial tick included
     * @return one at the end, falling linearly to zero a fade later
     */
    public static float strength(float goneSince, float now) {
        return Math.clamp(1f - (now - goneSince) / FADE_TICKS, 0f, 1f);
    }

    /**
     * A colour with its alpha scaled by a trace's strength.
     *
     * @param argb     the colour, alpha in the top byte
     * @param strength the trace's strength, zero to one
     * @return the colour faded
     */
    public static int fadeArgb(int argb, float strength) {
        int alpha = Math.round((argb >>> ALPHA_SHIFT) * strength);
        return Math.clamp(alpha, 0, OPAQUE_ALPHA) << ALPHA_SHIFT | argb & RGB_MASK;
    }
}
