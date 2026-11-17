package com.mercuriusxeno.goo.lunar;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntToDoubleFunction;

/**
 * The ejecta rays a fresh lunar crater throws: streaks of thrown rock radiating from its rim, each at its own uneven
 * angle, width and length, narrowing to nothing at its own end, and broken along its run into a patchy splatter of
 * pale flecks in the regolith that thins as it goes, so no two craters' rays look alike. Only a fresh crater throws
 * them, about one in four.
 * decision lunar-surface-gains-giant-craters-mountains-rilles-and-boulder-fields
 */
public final class EjectaRays {

    /** The farthest any ray runs, in radii from its crater's centre. */
    public static final double REACH = 2.5;
    /** A ray runs at least this far, in radii, and up to {@link #REACH}. */
    static final double MIN_LENGTH = 1.6;
    /** The share of large craters fresh enough to throw rays. */
    static final double FRESH_CHANCE = 0.25;
    /** A ray's half width at the rim, in radians either side of its line, at its narrowest and how much wider. */
    static final double RIM_HALF_ANGLE_MIN = 0.05;
    static final double RIM_HALF_ANGLE_SPREAD = 0.1;
    /** How far a ray strays from even spacing, as a share of the spacing either way. */
    static final double ANGLE_STRAY = 0.35;
    /** The fewest rays a crater throws, and how many more it may. */
    static final int MIN_RAYS = 5;
    static final int RAY_SPREAD = 5;
    /** The rolls each ray takes: its stray, its width, its length. */
    static final int ROLLS_PER_RAY = 3;
    /** The splatter's clumps, in blocks across, and the share of them kept at the rim and lost by the ray's end. */
    static final int CLUMP_SIZE = 3;
    static final double CLUMP_KEEP_AT_RIM = 0.8;
    static final double CLUMP_FADE = 0.75;
    /** The share of a kept clump's blocks that fleck pale. */
    static final double FLECK_KEEP = 0.7;
    private static final double FULL_TURN = 2.0 * Math.PI;
    private static final int STRAY_ROLL = 0;
    private static final int WIDTH_ROLL = 1;
    private static final int LENGTH_ROLL = 2;
    private static final long CLUMP_SALT = 0x5DEECE66DL;
    private static final long FLECK_SALT = 0x9E3779B97F4A7C15L;
    /** The splitmix finaliser's multipliers and shift, which spread a column's coordinates over the whole long. */
    private static final long X_SPREAD = 0x632BE59BD9B4E019L;
    private static final long Z_SPREAD = 0x85157AF5L;
    private static final long FIRST_MIX = 0xFF51AFD7ED558CCDL;
    private static final long SECOND_MIX = 0xC4CEB9FE1A85EC53L;
    private static final int MIX_SHIFT = 33;
    /** A long's top 53 bits, scaled into [0, 1). */
    private static final double UNIT = 1.0 / (1L << 53);
    private static final int MANTISSA_SHIFT = 11;
    /** Where a ray's stray rolls centre, so it strays either way from even spacing. */
    private static final double STRAY_CENTRE = 0.5;
    /** The share along a ray a column off every ray answers. */
    static final double OFF_EVERY_RAY = -1.0;

    private EjectaRays() {
    }

    /**
     * One ray: the angle it runs out at, its half width at the rim, and how far it runs.
     *
     * @param angle         its line's angle about the crater's centre, in radians
     * @param rimHalfAngle  its half width at the rim, in radians
     * @param length        how far it runs, in radii from the crater's centre
     */
    public record Ray(double angle, double rimHalfAngle, double length) {
    }

    /**
     * Rolls a fresh crater's rays: a count of them spread round the crater from a phase, each straying from even
     * spacing and taking its own width and length.
     *
     * @param countRoll the roll for how many rays, in [0, 1)
     * @param phase     the angle of the first ray's even spacing
     * @param rayRoll   each ray roll by its index from zero, in [0, 1)
     * @return the rays
     */
    static List<Ray> rays(double countRoll, double phase, IntToDoubleFunction rayRoll) {
        int count = MIN_RAYS + (int) (countRoll * RAY_SPREAD);
        double spacing = FULL_TURN / count;
        List<Ray> rays = new ArrayList<>(count);
        for (int ray = 0; ray < count; ray++) {
            int base = ray * ROLLS_PER_RAY;
            double stray = (rayRoll.applyAsDouble(base + STRAY_ROLL) - STRAY_CENTRE) * (ANGLE_STRAY + ANGLE_STRAY) * spacing;
            rays.add(new Ray(phase + ray * spacing + stray,
                    RIM_HALF_ANGLE_MIN + rayRoll.applyAsDouble(base + WIDTH_ROLL) * RIM_HALF_ANGLE_SPREAD,
                    MIN_LENGTH + rayRoll.applyAsDouble(base + LENGTH_ROLL) * (REACH - MIN_LENGTH)));
        }
        return rays;
    }

    /**
     * How far along a ray a column lies, if it lies on one: zero at the rim, nearing one at the ray's end.
     *
     * @param angle the column's angle about the crater's centre, in radians
     * @param reach the column's distance from the centre, in radii
     * @param rays  the crater's rays
     * @return the share of the way along the ray it lies on, or below zero off every ray
     */
    static double along(double angle, double reach, List<Ray> rays) {
        if (reach < 1.0) {
            return OFF_EVERY_RAY;
        }
        for (Ray ray : rays) {
            if (reach >= ray.length()) {
                continue;
            }
            double run = (reach - 1.0) / (ray.length() - 1.0);
            double offset = Math.abs(Math.IEEEremainder(angle - ray.angle(), FULL_TURN));
            if (offset < ray.rimHalfAngle() * (1.0 - run)) {
                return run;
            }
        }
        return OFF_EVERY_RAY;
    }

    /**
     * Whether a column on a ray flecks pale: it lies in a kept clump of the splatter, fewer kept the further along
     * the ray it lies, and it is one of that clump's flecked blocks.
     *
     * @param x   the column's x
     * @param z   the column's z
     * @param run how far along the ray it lies, in [0, 1)
     * @return true where the ray flecks the column
     */
    static boolean flecks(int x, int z, double run) {
        double clumpKeep = CLUMP_KEEP_AT_RIM * (1.0 - CLUMP_FADE * run);
        return hash(Math.floorDiv(x, CLUMP_SIZE), Math.floorDiv(z, CLUMP_SIZE), CLUMP_SALT) < clumpKeep
                && hash(x, z, FLECK_SALT) < FLECK_KEEP;
    }

    /**
     * Whether a column lies flecked on one of a crater's rays.
     *
     * @param x       the column's x
     * @param z       the column's z
     * @param centerX the crater's centre x
     * @param centerZ the crater's centre z
     * @param radius  the crater's radius
     * @param rays    the crater's rays
     * @return true where a ray flecks the column
     */
    static boolean isOnRayOf(int x, int z, double centerX, double centerZ, double radius, List<Ray> rays) {
        double dx = x - centerX;
        double dz = z - centerZ;
        double run = along(Math.atan2(dz, dx), Math.hypot(dx, dz) / radius, rays);
        return run >= 0.0 && flecks(x, z, run);
    }

    /**
     * A value in [0, 1) mixed from a column's coordinates and a salt, the same on every call.
     *
     * @param x    the column's x
     * @param z    the column's z
     * @param salt keeps one use of the hash apart from another
     * @return the value
     */
    private static double hash(int x, int z, long salt) {
        long mixed = salt ^ (x * X_SPREAD) ^ (z * Z_SPREAD);
        mixed = (mixed ^ (mixed >>> MIX_SHIFT)) * FIRST_MIX;
        mixed = (mixed ^ (mixed >>> MIX_SHIFT)) * SECOND_MIX;
        mixed ^= mixed >>> MIX_SHIFT;
        return (mixed >>> MANTISSA_SHIFT) * UNIT;
    }
}
