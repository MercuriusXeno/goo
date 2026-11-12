package com.mercuriusxeno.goo.throwing;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The cone a stream's vortex fills: its apex at the glove hand, its axis the
 * player's whole look, pitch and yaw, reaching the delivery's range and
 * widening by the delivery's cone angle, so it bounds every point
 * {@link StreamCone#contains} strikes.
 * drain-casts-a-filled-tornadic-miasma-vortex-along-the-look
 *
 * @param apex   the glove hand the vortex opens from
 * @param axis   the unit look it runs along
 * @param length its reach along the axis, the delivery's range in blocks
 * @param tan    the tangent of half the delivery's cone angle
 */
public record VortexCone(Vec3 apex, Vec3 axis, double length, double tan) {

    private static final double HALF = 0.5;

    /**
     * The vortex of a stream held from a hand along a look.
     *
     * @param hand        the glove hand
     * @param look        the player's look, any length
     * @param range       the delivery's range in blocks
     * @param coneDegrees the delivery's cone angle, rim to rim, in degrees
     * @return the vortex
     */
    public static VortexCone along(Vec3 hand, Vec3 look, double range, double coneDegrees) {
        return new VortexCone(hand, look.normalize(), range, Math.tan(Math.toRadians(coneDegrees * HALF)));
    }

    /**
     * The vortex's radius a distance out along its axis.
     *
     * @param distance blocks from the apex along the axis
     * @return the radius in blocks
     */
    public double radiusAt(double distance) {
        return distance * tan;
    }

    /**
     * Whether a point lies inside the vortex: ahead of the apex, no farther
     * along the axis than its length, and no farther off the axis than its
     * radius there.
     *
     * @param point the point tested
     * @return true for a point inside
     */
    public boolean bounds(Vec3 point) {
        Vec3 offset = point.subtract(apex);
        double along = offset.dot(axis);
        if (along < 0 || along > length) {
            return false;
        }
        return offset.subtract(axis.scale(along)).length() <= radiusAt(along);
    }

    /**
     * The box holding the whole vortex: the apex and the disc closing its far
     * end, which reaches each world axis's way by its radius times the share
     * of that way lying square to the vortex's axis.
     *
     * @param margin blocks the box stands out past the vortex each way
     * @return the box
     */
    public AABB box(double margin) {
        Vec3 far = apex.add(axis.scale(length));
        double radius = radiusAt(length);
        Vec3 disc = new Vec3(squareShare(axis.x), squareShare(axis.y), squareShare(axis.z)).scale(radius);
        return new AABB(far.subtract(disc), far.add(disc)).minmax(new AABB(apex, apex)).inflate(margin);
    }

    private static double squareShare(double component) {
        return Math.sqrt(Math.max(0, 1 - component * component));
    }
}
