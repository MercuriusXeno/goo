package com.mercuriusxeno.goo.client.radial;

/**
 * Eases the ring's displayed pose toward its target over a short duration
 * on the client tick: each type's slot width, how far open each type is and
 * the ring's rotation interpolate together, so opening a type plays like a
 * fan of cards and closing plays it in reverse. A new target restarts the
 * ease from the pose on display.
 * decision petal-moves-animate
 */
final class RingEase {

    /** Client ticks a petal move takes to land. */
    static final int DURATION_TICKS = 4;
    private static final double TWO_PI = 2.0 * Math.PI;
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
     * Starts an ease from the pose on display to a new target, turning the
     * target's rotation by whole turns to the one nearest the displayed
     * rotation, so the ring takes the short way round.
     *
     * @param target the pose to land on
     */
    void retarget(Pose target) {
        from = displayed(0.0f);
        double turns = Math.rint((from.rotation() - target.rotation()) / TWO_PI);
        to = target.turnedBy(turns * TWO_PI);
        ticks = 0;
    }

    /**
     * Turns the whole ring, the pose on display and the target alike, with
     * no ease, so the ring follows the cursor while the fan keeps playing.
     * decision ring-rotates-to-keep-the-cursor-inside
     *
     * @param turn the angle to add, clockwise
     */
    void turnBy(double turn) {
        from = from.turnedBy(turn);
        to = to.turnedBy(turn);
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
     * The ring as its rotation and, per type, the width of the slot it holds
     * and how far open it is: 0 shows the type's own petal at full length,
     * 1 its ability petals fanned across the slot.
     *
     * @param rotation the angle the first slot starts at, clockwise from the top
     * @param widths   each type's slot width in radians
     * @param openness each type's openness, 0 to 1
     */
    record Pose(double rotation, double[] widths, double[] openness) {

        /**
         * The pose a fraction of the way to another.
         *
         * @param target   the other pose, of the same types
         * @param fraction 0 for this pose, 1 for the target
         * @return the interpolated pose
         */
        Pose toward(Pose target, double fraction) {
            return new Pose(mix(rotation, target.rotation(), fraction), mix(widths, target.widths(), fraction),
                    mix(openness, target.openness(), fraction));
        }

        /**
         * This pose turned by an angle.
         *
         * @param turn the angle to add, clockwise
         * @return the turned pose
         */
        Pose turnedBy(double turn) {
            return new Pose(rotation + turn, widths, openness);
        }

        private static double mix(double from, double to, double fraction) {
            return from + (to - from) * fraction;
        }

        private static double[] mix(double[] from, double[] to, double fraction) {
            double[] mixed = new double[from.length];
            for (int i = 0; i < from.length; i++) {
                mixed[i] = mix(from[i], to[i], fraction);
            }
            return mixed;
        }
    }
}
