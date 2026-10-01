package com.mercuriusxeno.goo.throwing;

import net.minecraft.world.phys.Vec3;

/**
 * The cone a stream delivery sprays: its apex at the glove hand, its axis
 * the player's look, reaching the delivery's range and opening to the
 * delivery's cone angle, apex angle edge to edge (decision stream-delivery-held-cone).
 */
public final class StreamCone {

    private static final double HALF = 0.5;

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
}
