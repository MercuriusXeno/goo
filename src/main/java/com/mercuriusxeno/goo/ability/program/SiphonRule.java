package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.block.crucible.CrucibleMath;

/**
 * Unmake's drink rules: the cone it drinks, how long a block takes to stream
 * in and what a block costs. A block streams in over the time the unstable
 * crucible would take to melt it; it gives back what the crucible would, at
 * twice the unstable fuel an unstable crucible burns melting the same block,
 * the price of doing it at will.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public final class SiphonRule {

    /** How far from the eye the cone reaches, in blocks. */
    public static final double RANGE = 7;
    /** The distance the cone is as wide as its square, the middle of its far half. */
    public static final double MID_RANGE = 5.5;
    /** The fuel a block costs, as a multiple of the unstable crucible's. */
    public static final int FUEL_FACTOR = 2;
    private static final double SLOWEST_SPEED = 0.01;
    private static final double HALF_BLOCK = 0.5;
    private static final double EDGE_TO_EDGE = 2;

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
     * The ticks a block takes to stream into the glove: the unstable
     * crucible's own time melting it, divided by the drink's speed.
     *
     * @param totalGoo the mB of goo the block holds
     * @param exponent the unstable crucible's melt exponent
     * @param speed    how much faster than the crucible the drink goes, 1 at its pace
     * @return the ticks, at least 1
     */
    public static int siphonTicks(long totalGoo, double exponent, double speed) {
        long ticks = CrucibleMath.meltTicks(totalGoo, exponent);
        return (int) Math.max(1, Math.round(ticks / Math.max(SLOWEST_SPEED, speed)));
    }

    /**
     * The cone's apex angle, edge to edge: one block wide at the eye, and at
     * {@link #MID_RANGE} as wide as the square the radius names, so a 3x3
     * there and one block up close.
     *
     * @param radius how far the square reaches from the cone's axis at mid range, 1 for a 3x3
     * @return the apex angle in degrees
     */
    public static double coneDegrees(int radius) {
        return EDGE_TO_EDGE * Math.toDegrees(Math.atan((radius + HALF_BLOCK) / MID_RANGE));
    }
}
