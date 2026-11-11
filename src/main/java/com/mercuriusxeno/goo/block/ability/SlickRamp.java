package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.program.SlickStep;
import net.minecraft.world.phys.Vec3;

/**
 * The ramp Kinetic's slick gives a thing trying to move on it each tick: it
 * starts at the slick step's starting ramp and grows evenly to its strongest
 * over the step's build ticks, under the one fixed top speed.
 * slick-coats-the-ground-with-kinetic-speed
 * slick-ramp-grows-while-a-thing-keeps-moving
 */
public final class SlickRamp {

    private SlickRamp() {
    }

    /**
     * The ramp for a tick of trying.
     *
     * @param triedBefore the ticks the thing kept trying before this one
     * @param slick       the slick step naming the ramp
     * @return the factor this tick multiplies its horizontal velocity by
     */
    public static double ramp(int triedBefore, SlickStep slick) {
        double built = Math.min(1, (double) triedBefore / slick.buildTicks());
        return slick.startRamp() + (slick.strongestRamp() - slick.startRamp()) * built;
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
