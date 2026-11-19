package com.mercuriusxeno.goo.ability.astral;

import com.mercuriusxeno.goo.lunar.Lunar;
import net.minecraft.world.level.MoonPhase;

/**
 * Where the moon lands a caster on lunar. The light side's width splits into one eighth a moon phase, in phase order;
 * the night, from moonrise to moonset, sweeps x evenly through the phase's eighth one constant step a tick, so the
 * same tick of the same phase lands the same x and a second's miss lands some 1,800 blocks off. z is the average of
 * the caster's overworld x and z, wrapped into the moon's range.
 * decision time-and-phase-set-x-and-your-overworld-spot-sets-z
 * decision traversal-to-the-moon-always-lands-on-the-light-side
 */
public final class LunarLanding {

    /** The overworld clock's ticks a day. */
    static final int DAY_TICKS = 24_000;
    /**
     * The first and last day ticks the moon stands above the horizon: vanilla's day timeline turns the moon angle
     * from 180 degrees at day tick 6000 through a full turn on an eased curve, and the moon clears the horizon while
     * the angle stands within a quarter turn of overhead.
     */
    static final int MOONRISE_TICK = 12_782;
    static final int MOONSET_TICK = 23_218;
    static final int NIGHT_TICKS = MOONSET_TICK - MOONRISE_TICK + 1;
    /** One phase's share of the light side's width, in blocks. */
    private static final double PHASE_WIDTH = 2.0 * Lunar.LIGHT_SIDE_HALF_WIDTH / MoonPhase.COUNT;
    /** How far along x each tick of the night carries the landing, in blocks. */
    private static final double NIGHT_STEP = PHASE_WIDTH / NIGHT_TICKS;
    /** The moon's range along z, in blocks, until its border stands, centered on z 0. */
    private static final double DEPTH = 15_000_000;
    private static final double HALF_DEPTH = DEPTH / 2;
    /** The overworld spot's two coordinates, x and z, that z averages. */
    private static final double SPOT_COORDINATES = 2;

    private LunarLanding() {
    }

    /**
     * A landing column on lunar.
     *
     * @param x the column's x
     * @param z the column's z
     */
    public record Spot(double x, double z) {
    }

    /**
     * The ticks since moonrise an overworld clock time stands at, held inside the night: a tick before moonrise reads
     * as moonrise and one after moonset as moonset, so every landing stays inside its phase's eighth.
     *
     * @param overworldClockTime the overworld clock's total ticks
     * @return the night tick, 0 at moonrise to NIGHT_TICKS - 1 at moonset
     */
    public static int nightTickAt(long overworldClockTime) {
        int dayTick = (int) Math.floorMod(overworldClockTime, (long) DAY_TICKS);
        return Math.clamp(dayTick - MOONRISE_TICK, 0, NIGHT_TICKS - 1);
    }

    /**
     * The column the moon lands a caster on.
     *
     * @param phase      the moon phase's index, 0 to 7
     * @param nightTick  the ticks since moonrise, as nightTickAt answers
     * @param overworldX the caster's overworld x
     * @param overworldZ the caster's overworld z
     * @return the landing column
     */
    public static Spot at(int phase, int nightTick, double overworldX, double overworldZ) {
        double x = -Lunar.LIGHT_SIDE_HALF_WIDTH + phase * PHASE_WIDTH + nightTick * NIGHT_STEP;
        return new Spot(x, wrapIntoDepth((overworldX + overworldZ) / SPOT_COORDINATES));
    }

    private static double wrapIntoDepth(double z) {
        return z - DEPTH * Math.floor((z + HALF_DEPTH) / DEPTH);
    }
}
