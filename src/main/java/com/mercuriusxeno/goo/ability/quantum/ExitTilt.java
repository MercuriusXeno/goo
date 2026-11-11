package com.mercuriusxeno.goo.ability.quantum;

import net.minecraft.world.phys.Vec3;

/**
 * How a body coming out of a linked pair is tilted from upright: its up,
 * turned through the pair, against world up. A floor gate into a wall gate
 * lays it head into the wall and feet out, a quarter turn about the exit's
 * right; a pair keeping up as up tilts nothing. The tilt eases to nothing
 * over about half a second, so the body swings to standing rather than
 * popping out upright.
 * bodies-emerge-along-their-travel-and-ease-upright
 *
 * @param axis    the axis the body is turned about, a unit vector
 * @param degrees how far it is turned from upright as it emerges
 */
public record ExitTilt(Vec3 axis, double degrees) {

    /**
     * The tilt a body comes out of a pair with.
     *
     * @param entered the gate it went into
     * @param exit    the partner it came out of
     * @return the tilt
     */
    public static ExitTilt between(Gate entered, Gate exit) {
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 turnedUp = PortalTransform.between(entered, exit).direction(up).normalize();
        double degrees = Math.toDegrees(Math.acos(Math.clamp(turnedUp.dot(up), -UNIT, UNIT)));
        Vec3 axis = up.cross(turnedUp);
        // Turned a half turn, or not at all, the axis is any level one; the exit's right is the body's own.
        return new ExitTilt(axis.length() < LEVEL ? exit.up().cross(exit.face().getUnitVec3()).normalize()
                : axis.normalize(), degrees < STRAIGHT ? 0 : degrees);
    }

    /**
     * How far the body is still turned some ticks after it emerged: an even
     * swing over EASE_TICKS that starts and settles gently, smoothstep, so it
     * reads as a swing to standing rather than a flicker gone in the first
     * few ticks.
     *
     * @param ticksSince the ticks since it emerged, partial ticks and all
     * @return the degrees still turned
     */
    public double degreesAt(double ticksSince) {
        double done = Math.clamp(ticksSince / EASE_TICKS, 0.0, 1.0);
        return degrees * (1 - done * done * (SMOOTHSTEP_RISE - SMOOTHSTEP_BEND * done));
    }

    /** Smoothstep's terms: 3t^2 - 2t^3. */
    private static final double SMOOTHSTEP_RISE = 3.0;
    private static final double SMOOTHSTEP_BEND = 2.0;

    /**
     * The tilt still left some ticks after the body emerged.
     *
     * @param ticksSince the ticks since it emerged, partial ticks and all
     * @return the tilt left, about the same axis
     */
    public ExitTilt at(double ticksSince) {
        return new ExitTilt(axis, degreesAt(ticksSince));
    }

    /**
     * @return whether the body is turned at all
     */
    public boolean tilts() {
        return degrees > 0;
    }

    /** About half a second, the ticks a tilt takes to ease to nothing. */
    public static final double EASE_TICKS = 10;
    /** An up turned this little reads as not turned, its axis left level. */
    private static final double LEVEL = 1.0E-6;
    /** The cosine's bounds, which rounding can carry a unit vectors' dot just past. */
    private static final double UNIT = 1.0;
    /** A turn of less than this many degrees reads as none. */
    private static final double STRAIGHT = 0.5;
}
