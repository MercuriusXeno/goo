package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.client.ability.Afterimages;

/**
 * The mark the aimed mob carries beside its goo-colored outline: a toned-down
 * silhouette ripple over its model, one faint silhouette leaving the body each
 * period, growing out and fading, so the target reads by depth and motion as
 * well as by color. Built on the shared afterimage ripple.
 * active-target-indicators-carry-depth-and-animation
 * afterimage-is-one-shared-effect
 *
 * @param strength how strongly the mark shows, 0 for a mob the aim does not hold, up to 1
 * @param phase    how far through its period the ripple is, 0 to 1
 */
record AimedMobMark(float strength, float phase) {

    /** Game ticks between one silhouette leaving the aimed mob and the next; tune for a slower or faster pulse. */
    static final int PERIOD_TICKS = 30;

    /** How strongly the ripple shows, 0 to 1; tune to quiet or raise it. */
    static final float STRENGTH = 0.35f;

    /** Blocks the silhouette has grown out from the body when it has faded; tune for a tighter or wider ripple. */
    static final float GROWTH_BLOCKS = 0.25f;

    /** No mark: a mob the aim does not hold. */
    static final AimedMobMark NONE = new AimedMobMark(0f, 0f);

    /** Opacity in the vertex color's alpha byte. */
    private static final int FULL_ALPHA = 0xFF;

    /**
     * The mark a mob carries at a game time.
     *
     * @param aimed    whether the aim holds the mob
     * @param gameTime the game time including the partial tick
     * @return the mark, NONE for a mob the aim does not hold
     */
    static AimedMobMark at(boolean aimed, float gameTime) {
        if (!aimed) {
            return NONE;
        }
        double periods = gameTime / (double) PERIOD_TICKS;
        return new AimedMobMark(STRENGTH, (float) (periods - Math.floor(periods)));
    }

    /**
     * The ripple's silhouette as this mark stands: grown out on an ease-out
     * and faded linearly over the period, at the mark's strength.
     *
     * @return the silhouette
     */
    Afterimages.Pulse pulse() {
        float remaining = 1f - phase;
        return new Afterimages.Pulse(GROWTH_BLOCKS * (1f - remaining * remaining),
                Math.round(strength * remaining * FULL_ALPHA), phase);
    }
}
