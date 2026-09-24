package com.mercuriusxeno.goo.client;

/**
 * Client-side ripple agitation of one vat or crucible surface (decision
 * undulating-fluid-surface): the ripple rises while the level moves or a
 * stream pours and settles back to the resting ripple once the level holds.
 * Advanced at most once per game tick, so several frames in one tick
 * answer the same amplitude.
 */
public final class SurfaceAgitation {

    /** Agitation gained per unit of fill fraction the level moved. */
    static final float LEVEL_GAIN = 3f;
    /** Agitation gained per tick per mB/tick a stream pours. */
    static final float STREAM_GAIN = 0.0001f;
    /** Most agitation a stream adds in one tick, however fast it pours. */
    static final float STREAM_GAIN_CAP = 0.004f;
    /** The most agitation a surface holds, in blocks above the resting ripple. */
    public static final float AGITATION_CEILING = 0.05f;
    /** Fraction of agitation kept each tick. */
    static final float DECAY_KEPT = 0.92f;
    /** Agitation shed each tick past the proportional decay, so it reaches rest. */
    static final float DECAY_FLOOR_STEP = 0.0002f;
    /** Ticks past which a gap in updates has settled the surface anyway. */
    static final int MAX_CATCH_UP_TICKS = 200;

    private static final long NEVER_TICKED = Long.MIN_VALUE;

    private float agitation;
    private float lastFill = Float.NaN;
    private long lastTick = NEVER_TICKED;

    /**
     * Advances the agitation to the given game tick and answers the ripple
     * amplitude the surface shows.
     *
     * @param fillFraction the fill fraction the surface stands at, in [0, 1]
     * @param streamRate   the rate a stream pours in, in mB/tick, zero when none
     * @param gameTick     the current game time; a repeated tick answers without advancing
     * @return the resting ripple amplitude plus the current agitation, in blocks
     */
    public float tick(float fillFraction, float streamRate, long gameTick) {
        if (gameTick != lastTick) {
            decay(elapsedTicks(gameTick));
            agitate(fillFraction, streamRate);
            lastTick = gameTick;
        }
        return amplitude();
    }

    /**
     * @return the resting ripple amplitude plus the current agitation, in blocks
     */
    public float amplitude() {
        return RenderContext.RESTING_RIPPLE_AMPLITUDE + agitation;
    }

    private int elapsedTicks(long gameTick) {
        if (lastTick == NEVER_TICKED || gameTick < lastTick) {
            return 1;
        }
        return (int) Math.min(gameTick - lastTick, MAX_CATCH_UP_TICKS);
    }

    private void decay(int ticks) {
        for (int i = 0; i < ticks && agitation > 0f; i++) {
            agitation = Math.max(0f, agitation * DECAY_KEPT - DECAY_FLOOR_STEP);
        }
    }

    private void agitate(float fillFraction, float streamRate) {
        float levelStep = Float.isNaN(lastFill) ? 0f : Math.abs(fillFraction - lastFill);
        float pour = Math.min(Math.max(streamRate, 0f) * STREAM_GAIN, STREAM_GAIN_CAP);
        agitation = Math.min(agitation + levelStep * LEVEL_GAIN + pour, AGITATION_CEILING);
        lastFill = fillFraction;
    }
}
