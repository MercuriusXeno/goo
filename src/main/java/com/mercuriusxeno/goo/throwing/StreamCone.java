package com.mercuriusxeno.goo.throwing;

import net.minecraft.world.phys.Vec3;

/**
 * The cone a stream delivery sprays: its apex at the glove hand, its axis
 * the player's look, reaching the delivery's range and opening to the
 * delivery's cone angle, apex angle edge to edge (decision stream-delivery-held-cone).
 */
public final class StreamCone {

    private static final double HALF = 0.5;
    /** An up vector to cross the axis with, swapped for east when the axis runs straight up or down. */
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final Vec3 EAST = new Vec3(1, 0, 0);
    private static final double NEARLY_VERTICAL = 0.99;
    private static final double FULL_TURN = 2 * Math.PI;

    private StreamCone() {
    }

    /**
     * Whether a point stands inside the cone: ahead of the apex, within the
     * range, and within half the cone angle of the axis.
     *
     * @param apex        the cone's apex
     * @param axis        the cone's axis, any length
     * @param range       the cone's reach in blocks
     * @param coneDegrees the cone's apex angle in degrees
     * @param point       the point tested
     * @return true for a point inside the cone
     */
    public static boolean contains(Vec3 apex, Vec3 axis, double range, double coneDegrees, Vec3 point) {
        Vec3 offset = point.subtract(apex);
        double distance = offset.length();
        if (distance > range) {
            return false;
        }
        double along = offset.dot(axis.normalize());
        return along > 0 && along >= distance * Math.cos(Math.toRadians(coneDegrees * HALF));
    }

    /**
     * A unit vector square to the axis, to build a cone's cross-section on.
     *
     * @param axis the axis, any length
     * @return a unit vector square to it
     */
    public static Vec3 side(Vec3 axis) {
        Vec3 forward = axis.normalize();
        return forward.cross(Math.abs(forward.y) > NEARLY_VERTICAL ? EAST : UP).normalize();
    }

    /**
     * A direction leaving the apex inside the cone: tilted off the axis by a
     * share of the cone's half angle and turned about the axis by a share of
     * a full turn, so even shares fill the cone's mouth.
     * mycosis-spore-stream-buds-and-poisons
     *
     * @param axis        the cone's axis, any length
     * @param coneDegrees the cone's apex angle in degrees
     * @param tiltShare   how far off the axis, zero on it and one at the rim
     * @param turnShare   how far about the axis, zero to one for a full turn
     * @return the unit direction
     */
    public static Vec3 launchDirection(Vec3 axis, double coneDegrees, double tiltShare, double turnShare) {
        Vec3 forward = axis.normalize();
        Vec3 side = side(forward);
        Vec3 lift = side.cross(forward);
        double tilt = Math.toRadians(coneDegrees * HALF) * tiltShare;
        double turn = FULL_TURN * turnShare;
        Vec3 off = side.scale(Math.cos(turn)).add(lift.scale(Math.sin(turn)));
        return forward.scale(Math.cos(tilt)).add(off.scale(Math.sin(tilt))).normalize();
    }
}
