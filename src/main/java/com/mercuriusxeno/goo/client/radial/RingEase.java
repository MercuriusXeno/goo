package com.mercuriusxeno.goo.client.radial;

/**
 * The wheel's easing: each moving part, the types dropping and each type's
 * fan, steps its own tick count toward its target, so opening one fan never
 * restarts another's fold, and reads that count eased between ticks.
 * decision petal-moves-animate
 */
final class RingEase {

    /** Client ticks a petal move takes to land. */
    static final int DURATION_TICKS = 4;
    private static final double SMOOTHSTEP_RISE = 3.0;
    private static final double SMOOTHSTEP_EASE = 2.0;

    private RingEase() {
    }

    /**
     * A part's tick count stepped one tick toward its target.
     *
     * @param ticks  the part's tick count
     * @param target the count it moves toward, 0 or {@link #DURATION_TICKS}
     * @return the count after one tick
     */
    static int stepped(int ticks, int target) {
        return ticks + Integer.signum(target - ticks);
    }

    /**
     * How far a part has moved, eased between ticks.
     *
     * @param ticks       the part's tick count
     * @param target      the count it moves toward
     * @param partialTick the fraction of a tick since the last one
     * @return 0 at rest to 1 fully moved
     */
    static double eased(int ticks, int target, float partialTick) {
        double moving = Integer.signum(target - ticks) * partialTick;
        double progress = Math.max(0.0, Math.min(1.0, (ticks + moving) / DURATION_TICKS));
        return progress * progress * (SMOOTHSTEP_RISE - SMOOTHSTEP_EASE * progress);
    }

    /**
     * The wheel as how far the types have dropped into the neutral zone, 0
     * out at the tips to 1 dropped, and per type how far its abilities have
     * fanned out.
     * decision abilities-replace-the-hovered-type
     *
     * @param dropped  how far the types have dropped into the neutral zone, 0 to 1
     * @param openness each type's openness, 0 closed to 1 fanned
     */
    record Pose(double dropped, double[] openness) {
    }
}
