package com.mercuriusxeno.goo.lunar;

/**
 * Lunar's sun, which follows the phase cycle across the whole moon. One spot on the moon stands under the sun, and it
 * travels once around the moon every phase cycle, eight overworld days: at the overworld's midnight under its full
 * moon the sun stands behind the planet, where the planet stands at every x, and half a cycle on it stands opposite.
 * The sun's angle at an x is the planet's angle there turned by how far the cycle has run, growing with the clock so
 * the sun rises in the east and sets in the west everywhere.
 * decision sun-follows-the-phase-and-lights-the-far-side-too
 * decision lunar-has-a-day-and-night-and-a-sun-on-the-overworlds-clock
 */
public final class LunarSun {

    /** The overworld clock's ticks one phase cycle spans: eight overworld days, one lunar day. */
    static final long CYCLE_TICKS = 192_000;
    /** The overworld clock time of the midnight under the first full moon, when the sun stands behind the planet. */
    static final long FULL_MOON_MIDNIGHT = 18_000;
    private static final double FULL_TURN_DEGREES = 360.0;
    /** The overworld clock's ticks a day, the period lunar_day's light tracks run on. */
    private static final double DAY_TICKS = 24_000;
    /** The day tick lunar_day's sun angle stands overhead at. */
    private static final double NOON_TICK = 6_000;
    /** lunar_day's sun angle ease, a cubic bezier's two inner control points as vanilla's day timeline eases it. */
    private static final double EASE_X1 = 0.362;
    private static final double EASE_Y1 = 0.241;
    private static final double EASE_X2 = 0.638;
    private static final double EASE_Y2 = 0.759;
    private static final int EASE_INVERSE_STEPS = 40;
    private static final int CUBIC_WEIGHT = 3;
    private static final double MIDPOINT = 0.5;

    private LunarSun() {
    }

    /**
     * The sun's angle over a lunar x, as a sun angle reads: 0 overhead, 90 on the western horizon, 180 underfoot and
     * 270 on the eastern horizon.
     *
     * @param x                  the x, in blocks
     * @param overworldClockTime the overworld clock's total ticks
     * @return the angle, in degrees, from 0 up to 360
     */
    public static double sunAngleDegrees(double x, long overworldClockTime) {
        double cycleRun = Math.floorMod(overworldClockTime - FULL_MOON_MIDNIGHT, CYCLE_TICKS) / (double) CYCLE_TICKS;
        double angle = Lunar.planetAngleDegrees(x) + cycleRun * FULL_TURN_DEGREES;
        return angle - FULL_TURN_DEGREES * Math.floor(angle / FULL_TURN_DEGREES);
    }

    /**
     * The overworld day tick at which lunar_day's eased sun stands where lunar's sun stands over an x now, so lunar_day's
     * light tracks, laid against that sun on the overworld's 24,000 tick day, light the x as they light the overworld
     * under the same sun.
     *
     * @param x                  the x, in blocks
     * @param overworldClockTime the overworld clock's total ticks
     * @return the day tick, from 0 up to 24,000
     */
    public static double dayTickAt(double x, long overworldClockTime) {
        double easedShare = sunAngleDegrees(x, overworldClockTime) / FULL_TURN_DEGREES;
        double tick = NOON_TICK + timeShareOf(easedShare) * DAY_TICKS;
        return tick - DAY_TICKS * Math.floor(tick / DAY_TICKS);
    }

    /**
     * The share of lunar_day's sun segment that has run when its eased angle has run a share of its turn: the eased
     * curve's inverse, found by halving the bezier's parameter.
     *
     * @param easedShare the share of the turn the eased angle has run, 0 to 1
     * @return the share of the day's ticks, 0 to 1
     */
    private static double timeShareOf(double easedShare) {
        double low = 0.0;
        double high = 1.0;
        for (int step = 0; step < EASE_INVERSE_STEPS; step++) {
            double middle = (low + high) * MIDPOINT;
            if (bezier(middle, EASE_Y1, EASE_Y2) < easedShare) {
                low = middle;
            } else {
                high = middle;
            }
        }
        return bezier((low + high) * MIDPOINT, EASE_X1, EASE_X2);
    }

    /**
     * One coordinate of a cubic bezier from 0 to 1 through two inner control points.
     *
     * @param parameter the curve's parameter, 0 to 1
     * @param first     the first inner control point's coordinate
     * @param second    the second inner control point's coordinate
     * @return the coordinate at the parameter
     */
    private static double bezier(double parameter, double first, double second) {
        double rest = 1.0 - parameter;
        return CUBIC_WEIGHT * rest * rest * parameter * first + CUBIC_WEIGHT * rest * parameter * parameter * second
                + parameter * parameter * parameter;
    }
}
