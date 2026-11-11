package com.mercuriusxeno.goo.block.ability;

import net.minecraft.world.phys.Vec3;

/**
 * The ramp Kinetic's slick gives whatever moves on it each tick.
 * slick-coats-the-ground-with-kinetic-speed
 */
public final class SlickRamp {

    private SlickRamp() {
    }

    /**
     * A velocity ramped by the slick.
     *
     * @param velocity the velocity of the thing on the slick
     * @param ramp     the factor its horizontal velocity is multiplied by
     * @param topSpeed the horizontal speed the ramp holds it to, in blocks a tick
     * @return the ramped velocity
     */
    public static Vec3 ramped(Vec3 velocity, double ramp, double topSpeed) {
        double speed = velocity.horizontalDistance();
        double scale = speed * ramp > topSpeed ? topSpeed / speed : ramp;
        return new Vec3(velocity.x * scale, velocity.y, velocity.z * scale);
    }
}
