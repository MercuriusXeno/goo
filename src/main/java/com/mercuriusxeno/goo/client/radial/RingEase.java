package com.mercuriusxeno.goo.client.radial;

/**
 * Eases the wheel's displayed pose toward its target over a short duration
 * on the client tick: how far a type has opened, the types dropping into the
 * shrinking neutral zone, and how far each type's abilities have fanned out
 * interpolate together, so opening a type plays like a fan of cards and
 * closing plays it in reverse. A new target restarts the ease from the pose
 * on display.
 * decision petal-moves-animate
 */
final class RingEase {

    /** Client ticks a petal move takes to land. */
    static final int DURATION_TICKS = 4;
    private static final double SMOOTHSTEP_RISE = 3.0;
    private static final double SMOOTHSTEP_EASE = 2.0;

    private Pose from;
    private Pose to;
    private int ticks = DURATION_TICKS;

    /**
     * Creates an ease at rest on a pose.
     *
     * @param rest the pose on display before any move
     */
    RingEase(Pose rest) {
        this.from = rest;
        this.to = rest;
    }

    /**
     * Starts an ease from the pose on display to a new target.
     *
     * @param target the pose to land on
     */
    void retarget(Pose target) {
        from = displayed(0.0f);
        to = target;
        ticks = 0;
    }

    /** Advances the ease one client tick, holding once it lands. */
    void tick() {
        if (ticks < DURATION_TICKS) {
            ticks++;
        }
    }

    /**
     * Whether the ease is still moving the petals.
     * decision mid-animation-input-does-nothing
     *
     * @return true until the ease lands
     */
    boolean isRunning() {
        return ticks < DURATION_TICKS;
    }

    /**
     * The pose on display between ticks.
     *
     * @param partialTick the fraction of a tick since the last one
     * @return the interpolated pose
     */
    Pose displayed(float partialTick) {
        double progress = Math.min(1.0, (ticks + partialTick) / DURATION_TICKS);
        double eased = progress * progress * (SMOOTHSTEP_RISE - SMOOTHSTEP_EASE * progress);
        return from.toward(to, eased);
    }

    /**
     * The wheel as how far a type has opened, 0 with the types out at the tips
     * and the neutral zone wide to 1 with the types dropped into the neutral
     * zone, and per type how far its abilities have fanned out.
     * decision abilities-replace-the-hovered-type
     *
     * @param dropped  how far the types have dropped into the neutral zone, 0 to 1
     * @param openness each type's openness, 0 closed to 1 fanned
     */
    record Pose(double dropped, double[] openness) {

        /**
         * The pose a fraction of the way to another.
         *
         * @param target   the other pose, of the same types
         * @param fraction 0 for this pose, 1 for the target
         * @return the interpolated pose
         */
        Pose toward(Pose target, double fraction) {
            double[] mixed = new double[openness.length];
            for (int i = 0; i < openness.length; i++) {
                mixed[i] = mix(openness[i], target.openness()[i], fraction);
            }
            return new Pose(mix(dropped, target.dropped(), fraction), mixed);
        }

        private static double mix(double from, double to, double fraction) {
            return from + (to - from) * fraction;
        }
    }
}
