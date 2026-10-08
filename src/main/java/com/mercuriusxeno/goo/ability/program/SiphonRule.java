package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.block.crucible.CrucibleMath;

/**
 * Unmake's soup rules: how fast it drinks a block and what a block costs.
 * It is faster than the crucible on purpose; it gives back what the crucible
 * would, at twice the unstable fuel an unstable crucible burns melting the
 * same block, the price of doing it at will.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public final class SiphonRule {

    /** Ticks one block takes to stream into the soup, a quarter second. */
    public static final int SIPHON_TICKS = 5;
    /** Ticks between one block starting and the next, so a 3x3 drinks in about a second. */
    public static final int START_INTERVAL_TICKS = 2;
    /** The fuel a block costs, as a multiple of the unstable crucible's. */
    public static final int FUEL_FACTOR = 2;
    private static final double SLOWEST_SPEED = 0.01;

    private SiphonRule() {
    }

    /**
     * The unstable goo a block costs: twice the fuel the unstable crucible
     * burns melting it, one mB buying {@code ticksPerMb} ticks of its clock.
     *
     * @param slowestUnit the mB of the block's slowest melting unit
     * @param exponent    the unstable crucible's melt exponent
     * @param ticksPerMb  the heat ticks one mB of unstable goo buys
     * @return the mB burned
     */
    public static int fuelFor(long slowestUnit, double exponent, int ticksPerMb) {
        long ticks = CrucibleMath.meltTicks(slowestUnit, exponent);
        long fuel = FUEL_FACTOR * Math.ceilDiv(ticks, Math.max(1, ticksPerMb));
        return (int) Math.min(Integer.MAX_VALUE, fuel);
    }

    /**
     * @param speed how much faster than its pace the soup drinks, 1 at its pace
     * @return the ticks one block takes to stream in, at least 1
     */
    public static int siphonTicks(double speed) {
        return Math.max(1, (int) Math.round(SIPHON_TICKS / Math.max(SLOWEST_SPEED, speed)));
    }

    /**
     * @param speed how much faster than its pace the soup drinks, 1 at its pace
     * @return the ticks between one block starting and the next, at least 1
     */
    public static int startInterval(double speed) {
        return Math.max(1, (int) Math.round(START_INTERVAL_TICKS / Math.max(SLOWEST_SPEED, speed)));
    }
}
