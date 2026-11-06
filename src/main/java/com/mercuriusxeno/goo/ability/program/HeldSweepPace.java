package com.mercuriusxeno.goo.ability.program;

/**
 * The pace and the shell every held sweep shares: while the hold lasts the
 * sweep pulses, each pulse's sphere easing out from its center to its range
 * fast enough to feel inside the first half second, then resting a beat as
 * it fades before the next pulse starts; and what the sweep reveals draws
 * only in a thin shell at the sphere's rim, brightest at the rim itself.
 * decision held-sweeps-share-one-fast-shell-visual
 *
 * @param range the radius each pulse reaches, in blocks
 */
public record HeldSweepPace(double range) {

    /** Ticks a pulse's sphere takes to ease out to its range: a second. */
    public static final int EXPAND_TICKS = 20;
    /** Ticks a pulse rests at its range, fading, before the next pulse starts. */
    public static final int REST_TICKS = 6;
    /** The shell's thickness as a share of the radius, so it reads thin near and far. */
    public static final double SHELL_FRACTION = 0.08;
    private static final double WHOLE = 1.0;

    /**
     * The ticks one pulse runs, its expansion and its rest.
     *
     * @return the pulse's length in ticks
     */
    public int pulseTicks() {
        return EXPAND_TICKS + REST_TICKS;
    }

    /**
     * The tick of the current pulse a tick of the hold falls on: pulses run
     * one after another while the hold lasts.
     *
     * @param heldTicks the hold's age, 1 on its first tick
     * @return the pulse's tick, 1 on its first
     */
    public int pulseTick(int heldTicks) {
        return Math.floorMod(heldTicks - 1, pulseTicks()) + 1;
    }

    /**
     * The sphere's radius on a tick of its pulse: easing out, so the front
     * leaps away from the caster and slows as it nears its range.
     *
     * @param pulseTick the pulse's tick, 0 before it starts
     * @return the radius, held at the range once reached
     */
    public double radiusAt(int pulseTick) {
        double travelled = Math.clamp((double) pulseTick / EXPAND_TICKS, 0, WHOLE);
        double left = WHOLE - travelled;
        return range * (WHOLE - left * left);
    }

    /**
     * How strongly the pulse shows on a tick: whole while it expands, then
     * fading to nothing over its rest.
     *
     * @param pulseTick the pulse's tick, 1 on its first
     * @return the strength in [0, 1]
     */
    public float strengthAt(int pulseTick) {
        int resting = pulseTick - EXPAND_TICKS;
        return resting <= 0 ? 1f : (float) Math.clamp(WHOLE - (double) resting / REST_TICKS, 0, WHOLE);
    }

    /**
     * The shell's thickness at a radius.
     *
     * @param radius the sphere's radius
     * @return the thickness in blocks, the shell's share of the radius
     */
    public double shellThickness(double radius) {
        return SHELL_FRACTION * radius;
    }

    /**
     * Whether a cell at a distance from the center draws: only within the
     * shell, past its inner edge and no farther than the rim.
     *
     * @param distance the cell's distance from the center
     * @param radius   the sphere's radius
     * @return true when the cell lies in the shell
     */
    public boolean drawsAt(double distance, double radius) {
        return distance > radius - shellThickness(radius) && distance <= radius;
    }

    /**
     * How brightly a cell in the shell draws: whole at the rim, falling to
     * nothing at the shell's inner edge, so the shell reads as a sweeping band.
     *
     * @param distance the cell's distance from the center
     * @param radius   the sphere's radius
     * @return the brightness in [0, 1], 0 outside the shell
     */
    public float glowAt(double distance, double radius) {
        if (!drawsAt(distance, radius)) {
            return 0f;
        }
        return (float) (WHOLE - (radius - distance) / shellThickness(radius));
    }
}
