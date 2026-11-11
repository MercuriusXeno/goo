package com.mercuriusxeno.goo.client.ability;

/**
 * How much of Drain's vortex shows at a moment: when the hold starts it forms
 * out from the hand to its full length and thickens in, and when the hold
 * ends it thins away over the fade out rather than vanishing; a hold resumed
 * mid fade carries on from what still shows.
 * drain-casts-a-filled-tornadic-miasma-vortex-along-the-look
 */
final class VortexPresence {

    /** Ticks the vortex takes to thicken in. */
    static final double FADE_IN_TICKS = 6;
    /** Ticks the vortex takes to thin away. */
    static final double FADE_OUT_TICKS = 12;
    /** Ticks the vortex takes to reach out from the hand to its full length. */
    static final double FORM_TICKS = 5;

    private boolean holding;
    private double heldSince;
    private double releasedAt;
    private double presenceAtRelease;
    private double formedAtRelease;

    /**
     * What shows of the vortex.
     *
     * @param presence how thick it shows, nothing to full
     * @param formed   how far out from the hand it reaches, a share of its length
     */
    record Shown(double presence, double formed) {
    }

    /**
     * Steps the vortex to a moment.
     *
     * @param held whether the hold runs at this moment
     * @param now  the moment, in ticks with the partial tick
     * @return what shows
     */
    Shown update(boolean held, double now) {
        if (held && !holding) {
            Shown fading = fadingAt(now);
            heldSince = now - fading.presence() * FADE_IN_TICKS;
        } else if (!held && holding) {
            Shown last = heldAt(now);
            presenceAtRelease = last.presence();
            formedAtRelease = last.formed();
            releasedAt = now;
        }
        holding = held;
        return held ? heldAt(now) : fadingAt(now);
    }

    private Shown heldAt(double now) {
        double since = now - heldSince;
        return new Shown(Math.min(1, since / FADE_IN_TICKS), Math.min(1, since / FORM_TICKS));
    }

    private Shown fadingAt(double now) {
        double left = Math.max(0, 1 - (now - releasedAt) / FADE_OUT_TICKS);
        return new Shown(presenceAtRelease * left, formedAtRelease);
    }
}
