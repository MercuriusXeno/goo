package com.mercuriusxeno.goo;

import net.minecraft.world.phys.Vec3;

/**
 * Pure math for goo blob throw trajectories and the throw origin's reach.
 * Shared between client (preview arc, flight rendering) and server (throw
 * origin). Every method is side-agnostic and testable without framework state.
 */
public final class ThrowArc {

    /** Blocks of base peak per unit of distance raised to {@link #PEAK_EXPONENT}. */
    public static final double PEAK_SCALE = 0.2;

    /** Power of throw distance the base peak grows by. */
    public static final double PEAK_EXPONENT = 0.75;

    /** Flat boost the granny arc adds to the base peak, in blocks. */
    public static final double ARC_FLAT_BOOST = 1.0;

    /** Multiplier the granny arc applies to the base peak (1.15 = +15%). */
    public static final double GRANNY_PEAK_SCALE = 1.15;

    /**
     * Farthest the throw origin sits from the thrower's eye, in blocks at
     * scale 1: the rendered glove blob sits inside it, a forged origin does not.
     */
    public static final double HAND_REACH = 2.0;

    /**
     * Where the arc peaks as a fraction of total flight [0..1].
     * Values below 0.5 front-load the climb: the blob rises steeply
     * in the first portion then glides down more gently. 0.5 = symmetric.
     */
    public static final double ARC_PEAK_T = 0.5;

    /** Parabolic factor for "2 - s" envelope in the skewed arc rise phase. */
    private static final double ARC_RISE_FACTOR = 2.0;

    private ThrowArc() {}

    /**
     * Computes travel time in ticks for a given distance, growing with the
     * square root of distance so long throws stay flat (decision
     * flight-time-root-times-levity-plus-base).
     *
     * @param distance       world-space distance in blocks
     * @param levity         the thrown type's multiplier on the root of distance
     * @param baseFlightTime the thrown type's ticks of flight before distance adds any
     * @return travel ticks, always >= 1
     */
    public static double travelTicks(double distance, float levity, int baseFlightTime) {
        return Math.max(1, Math.ceil(Math.sqrt(distance) * levity + baseFlightTime));
    }

    /**
     * Computes the base peak height from throw distance as a power law, so
     * short throws barely lift and long throws arc without reaching the
     * linear arc's height.
     *
     * @param distance world-space distance in blocks
     * @return peak height in blocks
     */
    public static double basePeak(double distance) {
        return PEAK_SCALE * Math.pow(Math.max(0.0, distance), PEAK_EXPONENT);
    }

    /**
     * Computes the granny-arc boosted peak: 115% of the base peak + 1 block.
     *
     * @param distance world-space distance in blocks
     * @return boosted peak height in blocks
     */
    public static double grannyPeak(double distance) {
        return basePeak(distance) * GRANNY_PEAK_SCALE + ARC_FLAT_BOOST;
    }

    /**
     * Interpolates a point on an asymmetric arc with a given peak height.
     * The arc peaks at {@link #ARC_PEAK_T} instead of the midpoint,
     * producing a steep initial climb and a shallower descent. Two
     * parabolic segments are joined at the peak for C0 continuity.
     *
     * @param start arc origin (hand position)
     * @param end   arc destination (target center)
     * @param t     normalized progress [0..1]
     * @param peak  peak height in blocks (at t = ARC_PEAK_T)
     * @return world-space position on the arc
     */
    public static Vec3 arcPoint(Vec3 start, Vec3 end, double t, double peak) {
        double x = start.x + (end.x - start.x) * t;
        double y = start.y + (end.y - start.y) * t;
        double z = start.z + (end.z - start.z) * t;
        double arcY = skewedArc(t, peak, ARC_PEAK_T);
        return new Vec3(x, y + arcY, z);
    }

    /**
     * Piecewise parabolic arc: rises from 0 to peak over [0..tPeak],
     * falls from peak to 0 over [tPeak..1]. Each segment is a separate
     * quadratic so the climb rate and descent rate are independent.
     *
     * @param t     normalized progress [0..1]
     * @param peak  maximum height
     * @param tPeak where the peak occurs [0..1]
     * @return arc height at t
     */
    static double skewedArc(double t, double peak, double tPeak) {
        if (t <= tPeak) {
            double s = t / tPeak;
            return peak * s * (ARC_RISE_FACTOR - s);
        } else {
            double s = (t - tPeak) / (1.0 - tPeak);
            return peak * (1.0 - s * s);
        }
    }

    /**
     * Samples the arc into a polyline of evenly-spaced t values.
     *
     * @param start    arc origin
     * @param end      arc destination
     * @param peak     peak height in blocks
     * @param segments number of line segments (points = segments + 1)
     * @return sampled positions along the arc
     */
    public static Vec3[] sampleArc(Vec3 start, Vec3 end,
                                   double peak, int segments) {
        Vec3[] points = new Vec3[segments + 1];
        for (int i = 0; i <= segments; i++) {
            double t = (double) i / segments;
            points[i] = arcPoint(start, end, t, peak);
        }
        return points;
    }

    /**
     * Holds the throw origin the client sent within reach of the eye: an
     * origin inside the reach sphere passes through unchanged, one beyond it
     * is pulled onto the sphere along the line from the eye (decision
     * diagnose-then-fix-blob-off-the-line).
     *
     * @param eye    the thrower's eye position
     * @param origin the aim line's start the client sent
     * @param reach  the reach sphere's radius in blocks
     * @return the origin, clamped onto the reach sphere around the eye
     */
    public static Vec3 clampToReach(Vec3 eye, Vec3 origin, double reach) {
        Vec3 offset = origin.subtract(eye);
        double length = offset.length();
        if (length <= reach) {
            return origin;
        }
        return eye.add(offset.scale(reach / length));
    }
}
