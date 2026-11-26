package com.mercuriusxeno.goo.throwing;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The cone a stream's vortex fills: its apex at the glove hand or a nozzle, its axis the look it is poured along,
 * pitch and yaw, reaching the reach it is handed, its section the widths across and up and down of the
 * {@link ConeShape} it is handed, so it bounds every point {@link StreamCone#contains} strikes in that shape.
 * drain-casts-a-filled-tornadic-miasma-vortex-along-the-look
 * decision spray-abilities-render-procedurally-bound-to-their-cone
 *
 * @param apex   the hand or nozzle the vortex opens from
 * @param axis   the unit look it runs along
 * @param length its reach along the axis, in blocks
 * @param shape  its widths across and up and down
 */
public record VortexCone(Vec3 apex, Vec3 axis, double length, ConeShape shape) {

    private static final double HALF = 0.5;
    /** A cone this far along its axis tests no farther, so the length alone closes its far end. */
    private static final double PAST_ANY_END = Double.MAX_VALUE;

    /**
     * The vortex of a stream poured from a hand along a look.
     *
     * @param hand  the glove hand or nozzle
     * @param look  the look, any length
     * @param reach the reach in blocks
     * @param shape the cone's widths across and up and down
     * @return the vortex
     */
    public static VortexCone along(Vec3 hand, Vec3 look, double reach, ConeShape shape) {
        return new VortexCone(hand, look.normalize(), reach, shape);
    }

    /** @return the unit direction across the vortex's section, the side its shape's width across runs along */
    public Vec3 side() {
        return StreamCone.side(axis);
    }

    /** @return the unit direction up and down its section, the side its shape's lift runs along */
    public Vec3 lift() {
        return side().cross(axis);
    }

    /** @return half the shape's width across, in radians */
    public double halfSideAngle() {
        return Math.toRadians(shape.sideDegrees() * HALF);
    }

    /** @return half the shape's width up and down, in radians */
    public double halfLiftAngle() {
        return Math.toRadians(shape.liftDegrees() * HALF);
    }

    /**
     * The vortex's widest reach off its axis a distance out along it: the wider half width for a cone, the
     * corner for a band.
     *
     * @param distance blocks from the apex along the axis
     * @return the radius in blocks
     */
    public double widestRadiusAt(double distance) {
        double across = distance * Math.tan(halfSideAngle());
        double upDown = distance * Math.tan(halfLiftAngle());
        return shape.band() ? Math.hypot(across, upDown) : Math.max(across, upDown);
    }

    /**
     * Whether a point lies inside the vortex: ahead of the apex, no farther along the axis than its length, and
     * inside its shape there.
     *
     * @param point the point tested
     * @return true for a point inside
     */
    public boolean bounds(Vec3 point) {
        double along = point.subtract(apex).dot(axis);
        return along >= 0 && along <= length && StreamCone.contains(apex, axis, PAST_ANY_END, shape, point);
    }

    /**
     * The box holding the whole vortex: the apex and the disc closing its far end, which reaches each world axis's
     * way by its widest radius times the share of that way lying square to the vortex's axis.
     *
     * @param margin blocks the box stands out past the vortex each way
     * @return the box
     */
    public AABB box(double margin) {
        Vec3 far = apex.add(axis.scale(length));
        double radius = widestRadiusAt(length);
        Vec3 disc = new Vec3(squareShare(axis.x), squareShare(axis.y), squareShare(axis.z)).scale(radius);
        return new AABB(far.subtract(disc), far.add(disc)).minmax(new AABB(apex, apex)).inflate(margin);
    }

    private static double squareShare(double component) {
        return Math.sqrt(Math.max(0, 1 - component * component));
    }
}
