package com.mercuriusxeno.goo.client.radial;

/**
 * Eases the ring's displayed pose toward its target over a short duration
 * on the client tick, each petal's arc and the ring's rotation interpolated
 * together, so a boundary moves away from the cursor as the open type's span
 * grows around it rather than sweeping through it. A new target restarts the
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
        to = new Pose(target.rotation() + turns * TWO_PI, target.arcs());
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
     * The ring as its rotation and one arc per slot, every type's own petal
     * and every ability of every type in a fixed order, an absent petal's
     * arc zero, so any two poses interpolate slot by slot.
     *
     * @param rotation the angle the first slot starts at, clockwise from the top
     * @param arcs     each slot's arc in radians
     */
    record Pose(double rotation, double[] arcs) {

        /**
         * The pose a fraction of the way to another.
         *
         * @param target   the other pose, of the same slots
         * @param fraction 0 for this pose, 1 for the target
         * @return the interpolated pose
         */
        Pose toward(Pose target, double fraction) {
            double[] mixed = new double[arcs.length];
            for (int slot = 0; slot < arcs.length; slot++) {
                mixed[slot] = arcs[slot] + (target.arcs()[slot] - arcs[slot]) * fraction;
            }
            return new Pose(rotation + (target.rotation() - rotation) * fraction, mixed);
        }
    }
}
