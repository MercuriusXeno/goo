package com.mercuriusxeno.goo.lunar;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Lunar's gravity: one sixth of the overworld's, the real moon's, for everything that falls there, the overworld's
 * everywhere else. Every gravity the game or goo applies in a dimension is scaled by {@link #scaleIn}, and a thrown
 * goo blob, which flies a scripted arc to its aim point with no gravity of its own, floats there for
 * {@link #flightTicksIn}, the time one-sixth gravity takes to draw the same peak.
 * decision lunar-gravity-is-one-sixth-of-the-overworlds
 */
public final class LunarGravity {

    /** Lunar's gravity as a share of the overworld's: the real moon's, one sixth. */
    private static final double LUNAR_SCALE = 1.0 / 6.0;
    private static final double OVERWORLD_SCALE = 1.0;

    private LunarGravity() {
    }

    /**
     * The share of the overworld's gravity a dimension pulls with.
     *
     * @param dimension the dimension
     * @return one sixth in lunar, one everywhere else
     */
    public static double scaleIn(ResourceKey<Level> dimension) {
        return Lunar.LEVEL.equals(dimension) ? LUNAR_SCALE : OVERWORLD_SCALE;
    }

    /**
     * A thrown goo blob's flight time in a dimension: an arc's peak is gravity times the flight time squared over
     * eight, so the same peak under a weaker pull takes the flight time over the square root of the scale.
     *
     * @param dimension      the dimension the blob is thrown in
     * @param overworldTicks the ticks the same throw flies in the overworld
     * @return the ticks it flies in that dimension
     */
    public static int flightTicksIn(ResourceKey<Level> dimension, int overworldTicks) {
        return (int) Math.round(overworldTicks / Math.sqrt(scaleIn(dimension)));
    }
}
