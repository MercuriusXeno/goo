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
     * The velocity a thing leaves a tick on the slick with: a thing trying
     * to move that steps on from other ground slower than the start speed is
     * raised to it along its heading, since a ramp on a small speed gives
     * almost nothing; a thing trying otherwise is ramped; a thing not trying
     * only slides.
     * slick-raises-a-slow-entry-to-a-start-speed
     *
     * @param velocity           the velocity of the thing on the slick
     * @param stoodOnSlickBefore whether it stood on slick the tick before
     * @param trying             whether it tries to move
     * @param triedBefore        the ticks it kept trying before this one
     * @param slick              the slick step naming the ramp and the start speed
     * @return the velocity it leaves the tick with
     */
    public static Vec3 slid(Vec3 velocity, boolean stoodOnSlickBefore, boolean trying, int triedBefore,
                            SlickStep slick) {
        if (!trying) {
            return velocity;
        }
        double speed = velocity.horizontalDistance();
        if (!stoodOnSlickBefore && speed > 0 && speed < slick.startSpeed()) {
            return raisedTo(velocity, speed, slick.startSpeed());
        }
        return ramped(velocity, ramp(triedBefore, slick), slick.topSpeed());
    }

    /**
     * A velocity scaled along its heading to a horizontal speed no less than
     * the one asked: scaling by the ratio alone can round an ulp short, so
     * the scale climbs an ulp at a time until the speed is reached.
     * slick-raises-a-slow-entry-to-a-start-speed
     *
     * @param velocity the velocity to raise
     * @param speed    its horizontal speed, above zero
     * @param to       the horizontal speed it is raised to
     * @return the raised velocity
     */
    private static Vec3 raisedTo(Vec3 velocity, double speed, double to) {
        double scale = to / speed;
        Vec3 raised = new Vec3(velocity.x * scale, velocity.y, velocity.z * scale);
        while (raised.horizontalDistance() < to) {
            scale = Math.nextUp(scale);
            raised = new Vec3(velocity.x * scale, velocity.y, velocity.z * scale);
        }
        return raised;
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
