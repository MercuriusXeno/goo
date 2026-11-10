package com.mercuriusxeno.goo.ability.quantum;

import net.minecraft.world.phys.Vec3;

/**
 * The camera transform through a linked pair, as the portal gun draws it: a
 * point in front of the portal looked into maps to the matching point behind
 * its partner, turned half about the portals' up, so a camera looking into
 * one sees out of the other as though its side stood right there. Each
 * portal's frame is its face's normal, its up along its length (world up on
 * a wall, the caster's facing on a floor or ceiling) and the right between them.
 * portals-link-by-looking-through-portal-vision
 *
 * @param fromCenter the middle of the face looked into
 * @param fromRight  its right
 * @param fromUp     its up
 * @param fromNormal its outward normal
 * @param toCenter   the middle of the partner's face
 * @param toRight    the partner's right
 * @param toUp       the partner's up
 * @param toNormal   the partner's outward normal
 */
public record PortalTransform(Vec3 fromCenter, Vec3 fromRight, Vec3 fromUp, Vec3 fromNormal,
                              Vec3 toCenter, Vec3 toRight, Vec3 toUp, Vec3 toNormal) {

    /**
     * The transform from looking into one portal to looking out of its partner.
     *
     * @param from the portal looked into
     * @param to   its partner
     * @return the transform
     */
    public static PortalTransform between(Portal from, Portal to) {
        return between(from.gate(), to.gate());
    }

    /**
     * The transform from looking into one gate to looking out of its partner.
     *
     * @param from the gate looked into
     * @param to   its partner
     * @return the transform
     */
    public static PortalTransform between(Gate from, Gate to) {
        Vec3 fromNormal = from.face().getUnitVec3();
        Vec3 toNormal = to.face().getUnitVec3();
        return new PortalTransform(from.center(), from.up().cross(fromNormal), from.up(), fromNormal,
                to.center(), to.up().cross(toNormal), to.up(), toNormal);
    }

    /**
     * Maps a point through the pair.
     *
     * @param world a point
     * @return the matching point on the partner's side
     */
    public Vec3 point(Vec3 world) {
        return toCenter.add(direction(world.subtract(fromCenter)));
    }

    /**
     * Maps a direction through the pair: the frame of the face looked into,
     * turned half about up, laid onto the partner's frame.
     *
     * @param world a direction
     * @return the matching direction on the partner's side
     */
    public Vec3 direction(Vec3 world) {
        double right = world.dot(fromRight);
        double up = world.dot(fromUp);
        double out = world.dot(fromNormal);
        return toRight.scale(-right).add(toUp.scale(up)).add(toNormal.scale(-out));
    }
}
