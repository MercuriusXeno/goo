package com.mercuriusxeno.goo.ability.astral;

import com.mercuriusxeno.goo.lunar.Lunar;
import net.minecraft.world.level.MoonPhase;
import java.util.UUID;

/**
 * Where the moon lands a caster on lunar. The light side's width splits into one eighth a moon phase, in phase order;
 * the night, from moonrise to moonset, sweeps x evenly through the phase's eighth one constant step a tick, so the
 * same tick of the same phase lands the same x and a second's miss lands some 1,800 blocks off. z is a hash of the
 * caster's account UUID spread evenly across the band around z 0, so each player lands on their own stretch of the
 * light side at every phase and tick; the tick's step already moves each landing, so no random offset is added.
 * decision time-and-phase-set-x-and-your-overworld-spot-sets-z
 * decision z-is-a-function-of-the-account-uuid
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
    /** How far either side of z 0 a landing's z falls, in blocks: the operator's band. */
    private static final double Z_HALF_BAND = 50_000;
    private static final double Z_BAND = 2 * Z_HALF_BAND;
    /** The bits of a hash a double's mantissa holds, so the hash maps evenly onto [0, 1). */
    private static final int MANTISSA_BITS = 53;
    private static final double MANTISSA_UNIT = 0x1.0p-53;
    /** MurmurHash3's 64-bit finalizer: its two multipliers and its shift. */
    private static final long FINALIZER_FIRST = 0xff51afd7ed558ccdL;
    private static final long FINALIZER_SECOND = 0xc4ceb9fe1a85ec53L;
    private static final int FINALIZER_SHIFT = 33;

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
     * @param phase     the moon phase's index, 0 to 7
     * @param nightTick the ticks since moonrise, as nightTickAt answers
     * @param account   the caster's account UUID
     * @return the landing column
     */
    public static Spot at(int phase, int nightTick, UUID account) {
        double x = -Lunar.LIGHT_SIDE_HALF_WIDTH + phase * PHASE_WIDTH + nightTick * NIGHT_STEP;
        return new Spot(x, zOf(account));
    }

    /**
     * The z an account lands on, evenly across the band: both halves of the UUID are hashed, so sequential and offline
     * UUIDs, alike in one half or in their low bits, still land far apart.
     * decision z-is-a-function-of-the-account-uuid
     *
     * @param account the caster's account UUID
     * @return the landing's z, from -Z_HALF_BAND to +Z_HALF_BAND
     */
    private static double zOf(UUID account) {
        long lowHalf = avalancheBits(account.getLeastSignificantBits());
        long hash = avalancheBits(account.getMostSignificantBits() ^ lowHalf);
        double unit = (hash >>> (Long.SIZE - MANTISSA_BITS)) * MANTISSA_UNIT;
        return -Z_HALF_BAND + Z_BAND * unit;
    }

    private static long avalancheBits(long bits) {
        long mixed = (bits ^ (bits >>> FINALIZER_SHIFT)) * FINALIZER_FIRST;
        mixed = (mixed ^ (mixed >>> FINALIZER_SHIFT)) * FINALIZER_SECOND;
        return mixed ^ (mixed >>> FINALIZER_SHIFT);
    }
}
