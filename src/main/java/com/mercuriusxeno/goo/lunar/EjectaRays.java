package com.mercuriusxeno.goo.lunar;

/**
 * The ejecta rays a large lunar crater throws: bright streaks of thrown rock radiating from its rim at evenly spaced
 * angles, each widest at the rim and tapering to nothing at the rays' reach.
 * decision lunar-surface-gains-giant-craters-mountains-rilles-and-boulder-fields
 */
public final class EjectaRays {

    /** The rays run out to two and a half radii from the crater's centre. */
    public static final double REACH = 2.5;
    /** A ray's half width at the rim, in radians either side of its line. */
    static final double RIM_HALF_ANGLE = 0.12;
    /** The fewest rays a crater throws, and how many more it may. */
    static final int MIN_RAYS = 5;
    static final int RAY_SPREAD = 5;
    private static final double FULL_TURN = 2.0 * Math.PI;

    private EjectaRays() {
    }

    /**
     * Whether a column lies on one of a crater's rays.
     *
     * @param angle the column's angle about the crater's centre, in radians
     * @param reach the column's distance from the centre, in radii
     * @param rays  how many rays the crater throws
     * @param phase the angle of the crater's first ray, in radians
     * @return true on a ray, between the rim and the rays' reach
     */
    public static boolean isOnRay(double angle, double reach, int rays, double phase) {
        if (reach < 1.0 || reach >= REACH) {
            return false;
        }
        double spacing = FULL_TURN / rays;
        double offset = ((angle - phase) % spacing + spacing) % spacing;
        double fromNearestRay = Math.min(offset, spacing - offset);
        double halfAngle = RIM_HALF_ANGLE * (1.0 - (reach - 1.0) / (REACH - 1.0));
        return fromNearestRay < halfAngle;
    }

    /**
     * How many rays a crater throws, from one of its rolls.
     *
     * @param roll the roll, in [0, 1)
     * @return the ray count
     */
    static int rayCount(double roll) {
        return MIN_RAYS + (int) (roll * RAY_SPREAD);
    }

    /**
     * The angle of a crater's first ray, from one of its rolls.
     *
     * @param roll the roll, in [0, 1)
     * @return the angle, in radians
     */
    static double phase(double roll) {
        return roll * FULL_TURN;
    }

    /**
     * Whether a column lies on a ray of a crater centred at a point.
     *
     * @param x       the column's x
     * @param z       the column's z
     * @param centerX the crater's centre x
     * @param centerZ the crater's centre z
     * @param radius  the crater's radius
     * @param rays    how many rays the crater throws
     * @param phase   the angle of its first ray
     * @return true on one of its rays
     */
    static boolean isOnRayOf(int x, int z, double centerX, double centerZ, double radius, int rays, double phase) {
        double dx = x - centerX;
        double dz = z - centerZ;
        return isOnRay(Math.atan2(dz, dx), Math.hypot(dx, dz) / radius, rays, phase);
    }
}
