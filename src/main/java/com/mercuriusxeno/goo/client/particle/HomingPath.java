package com.mercuriusxeno.goo.client.particle;

import net.minecraft.world.phys.Vec3;

/**
 * The curve a homing mote flies: a quadratic bend from where it spawned,
 * pulled out through a control point, onto a target read again every tick,
 * so a mote homing on a moving glove or a walking mob still lands on it.
 *
 * @param start   where the mote spawned
 * @param control the point the curve bends out through
 */
public record HomingPath(Vec3 start, Vec3 control) {

    /**
     * The point on the curve at a share of the flight.
     *
     * @param target   where the mote lands, as it stands this tick
     * @param progress the share of the flight flown, zero to one
     * @return the mote's position
     */
    public Vec3 at(Vec3 target, double progress) {
        double p = Math.clamp(progress, 0.0, 1.0);
        double fromStart = (1 - p) * (1 - p);
        double fromControl = 1 - fromStart - p * p;
        double fromTarget = p * p;
        return start.scale(fromStart).add(control.scale(fromControl)).add(target.scale(fromTarget));
    }
}
