package com.mercuriusxeno.goo.block.crucible;

/**
 * Pure math and logic for the crucible, extracted so unit tests can
 * run without triggering Minecraft class initialization.
 */
public final class CrucibleMath {

    /** Absorbs floating error in an exact power, so 100 ^ 0.5 reads 10 ticks rather than 11. */
    private static final double POWER_TOLERANCE = 1e-9;

    private CrucibleMath() {
    }

    /**
     * The ticks an item melts in when it is alone in the crucible: ceil(mB ^ exponent),
     * at least one (decision melt-time-is-mb-to-a-power).
     *
     * @param volume   one item's whole goo value across its types, in mB
     * @param exponent the burning fuel's melt exponent
     * @return the melt time in ticks
     */
    public static long meltTicks(long volume, double exponent) {
        if (volume <= 0) {
            return 1;
        }
        return Math.max(1L, (long) Math.ceil(Math.pow(volume, exponent) - POWER_TOLERANCE));
    }

    /**
     * Moves a value toward a target by at most step, without overshooting.
     *
     * @param current the current value
     * @param target  the target position or property
     * @param step    the step
     * @return the float value
     */
    public static float moveToward(float current, float target, float step) {
        if (current < target) {
            return Math.min(current + step, target);
        }
        return Math.max(current - step, target);
    }
}
