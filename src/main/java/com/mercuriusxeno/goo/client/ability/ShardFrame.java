package com.mercuriusxeno.goo.client.ability;

import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Where one crystal shard sits and how it lies: its center, the unit long
 * axis and the unit perpendicular arm it spreads along, and how far it
 * reaches along each (decision render-context-is-the-one-emitter).
 *
 * @param center     the shard center
 * @param axis       the unit long axis
 * @param perp       the unit perpendicular arm
 * @param halfLength the reach along the long axis
 * @param halfWidth  the reach along the perpendicular arm
 */
record ShardFrame(Vector3fc center, Vector3fc axis, Vector3fc perp, float halfLength, float halfWidth) {

    /**
     * The point a given fraction of each reach away from the center.
     *
     * @param along  the fraction of the half-length along the long axis
     * @param across the fraction of the half-width along the perpendicular arm
     * @return the point
     */
    Vector3f at(float along, float across) {
        float axisReach = halfLength * along;
        float perpReach = halfWidth * across;
        return new Vector3f(
                center.x() + axis.x() * axisReach + perp.x() * perpReach,
                center.y() + axis.y() * axisReach + perp.y() * perpReach,
                center.z() + axis.z() * axisReach + perp.z() * perpReach);
    }

    /**
     * The shard's face normal, the long axis crossed with the perpendicular arm.
     *
     * @return the unit normal
     */
    Vector3f normal() {
        return axis.cross(perp, new Vector3f());
    }

    /**
     * The apex a pyramid of the given depth raises over the center.
     *
     * @param depth how far the apex stands off the center along the normal
     * @return the apex
     */
    Vector3f apex(float depth) {
        return normal().mul(depth).add(center);
    }
}
