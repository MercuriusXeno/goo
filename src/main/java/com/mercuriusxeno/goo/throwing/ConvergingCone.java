package com.mercuriusxeno.goo.throwing;

import net.minecraft.world.phys.Vec3;

/**
 * The cone a spray's drips land in: centered on the look from the eye and
 * reaching as far as the look lands, so each drip, thrown from the lifted
 * blob, flies a plain arc onto its own spot of the ellipse the cone makes
 * there. Where the look meets only air within the drips' reach, the cone
 * centers on where a drip thrown from the eye along the look comes down, so
 * a spray pitched at the sky lands on the caster's own line.
 * residual-spray-cone-centers-on-the-look-from-the-blob
 * spray-drips-land-around-what-the-look-points-at
 * goo-spray-scatters-in-a-cone-around-the-look-vector: only how far the look lands is read, never the block it meets
 *
 * @param eye   the cone's apex, the caster's eye
 * @param axis  the cone's unit axis: the look, or toward where a drip thrown from the eye comes down
 * @param lands how far from the eye the cone's drips land, in blocks
 */
public record ConvergingCone(Vec3 eye, Vec3 axis, double lands) {

    /** How far past the drips' reach the look is clipped, so a surface standing right at that reach still counts. */
    private static final double PAST_THE_REACH = 1;

    /**
     * The cone a look sprays: out to the first surface the look meets within
     * the drips' reach, or, where it meets only air, onto where a drip thrown
     * from the eye along the look at the exit speed comes down.
     *
     * @param eye       the caster's eye
     * @param look      the caster's look, any length
     * @param exitSpeed the drips' exit speed, blocks per tick
     * @param flight    what a drip's flight runs against
     * @return the cone
     */
    public static ConvergingCone along(Vec3 eye, Vec3 look, double exitSpeed, DripArc.Flight flight) {
        Vec3 ray = look.normalize();
        Vec3 comesDown = DripArc.flyToItsEnd(eye, ray.scale(exitSpeed), flight).position();
        double reach = comesDown.distanceTo(eye);
        return flight.clip().firstHit(eye, eye.add(ray.scale(reach + PAST_THE_REACH)))
                .map(hit -> new ConvergingCone(eye, ray, hit.point().distanceTo(eye)))
                .orElseGet(() -> onto(eye, comesDown));
    }

    /**
     * The cone centered on a point: its axis from the eye to it, landing as far off as it stands.
     * nectar-is-sprayed-goo-with-a-guided-throw-near-a-mob: a guided throw centers the cone on the mob's feet
     *
     * @param eye   the caster's eye
     * @param point the point the cone centers on
     * @return the cone
     */
    public static ConvergingCone onto(Vec3 eye, Vec3 point) {
        Vec3 line = point.subtract(eye);
        return new ConvergingCone(eye, line.normalize(), line.length());
    }

    /**
     * The spot a drip's share of the cone lands on: along its ray of the
     * cone from the eye, as far off as the cone lands.
     *
     * @param coneDegrees the cone, apex to rim, in degrees
     * @param share       the drip's place in the cone
     * @return the spot
     */
    public Vec3 spotOf(double coneDegrees, CoatRay.ConeShare share) {
        return eye.add(StreamCone.launchDirection(axis, coneDegrees, share.tilt(), share.turn()).scale(lands));
    }

    /**
     * A drip's launch from where it leaves onto its share's spot of the cone, at the exit speed.
     * goop-and-slick-drips-leave-at-one-exit-speed
     *
     * @param apex        where the drip leaves, the lifted blob
     * @param coneDegrees the cone, apex to rim, in degrees
     * @param share       the drip's place in the cone
     * @param exitSpeed   the drip's exit speed, blocks per tick
     * @param gravity     the pull on the drip each tick
     * @return the launch velocity, blocks per tick
     */
    public Vec3 launchFrom(Vec3 apex, double coneDegrees, CoatRay.ConeShare share, double exitSpeed,
                           double gravity) {
        return DripArc.launchOnto(apex, spotOf(coneDegrees, share), exitSpeed, gravity);
    }
}
