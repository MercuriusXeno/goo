package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.block.crucible.CrucibleMath;

/**
 * How long an unmake takes: the unstable crucible's own melt time for what it
 * melts. Each item stack is one unit melting in ceil(mB ^ exponent) ticks on
 * its whole value, and every unit melts at once as unstable fuel melts them,
 * so the slowest unit decides. The goo it gives back is the full goo the
 * crucible would; the goo the channel burns is its worse efficiency.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public final class UnmakeRule {

    /** The slowest an unmake may be sped down to, so no speed stops it. */
    private static final double MIN_SPEED = 0.01;

    private UnmakeRule() {
    }

    /**
     * The work an unmake takes: the unstable crucible's melt time for the
     * slowest unit, divided by the unmake's speed, never less than one.
     *
     * @param slowestUnit the goo value of the slowest unit, in mB: a block's item, or a loot stack whole
     * @param exponent    the unstable fuel's melt exponent
     * @param speed       how much faster than the crucible the unmake works, 1 at its pace
     * @return the work, in held ticks or drips
     */
    public static int workToUnmake(long slowestUnit, double exponent, double speed) {
        long ticks = CrucibleMath.meltTicks(slowestUnit, exponent);
        return (int) Math.max(1, Math.ceil(ticks / Math.max(speed, MIN_SPEED)));
    }
}
