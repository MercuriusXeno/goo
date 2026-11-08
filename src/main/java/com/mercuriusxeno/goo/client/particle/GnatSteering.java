package com.mercuriusxeno.goo.client.particle;

import com.mercuriusxeno.goo.throwing.StreamCone;
import net.minecraft.world.phys.Vec3;

/**
 * How a seeking gnat flies: it leaves the glove slow, sprayed wide around the
 * aim, and each tick its velocity eases a share of the way toward the
 * velocity that heads straight at the prey at cruising speed, so it speeds up
 * and bends round onto the mob over its whole flight instead of snapping onto
 * it, and slows as it arrives so it settles on the mob rather than
 * overshooting (decision decay-gnats-degrade-each-block-once).
 */
public final class GnatSteering {

    /** A seeking gnat's top speed, blocks per tick. */
    public static final double CRUISE = 0.5;
    /** A gnat's speed leaving the glove, blocks per tick. */
    private static final double LAUNCH_SPEED = 0.15;
    /** The spray a gnat launches inside, apex to rim, so the widest leaves 45 degrees off the aim. */
    private static final double SPRAY_DEGREES = 90;
    /** The share of the way toward a straight heading a gnat turns each tick. */
    private static final double TURN_SHARE = 0.12;
    /** Below this squared gap a gnat sits on its prey and has no heading to take. */
    private static final double ON_THE_PREY_SQUARED = 1e-6;

    private GnatSteering() {
    }

    /**
     * A seeking gnat's launch from the glove.
     *
     * @param aim       the heading from the glove onto the crosshair, any length
     * @param tiltShare how far off the aim, 0 on it and 1 at the spray's rim
     * @param turnShare which way round the aim, a share of a full turn
     * @return the launch velocity, blocks per tick
     */
    public static Vec3 launch(Vec3 aim, double tiltShare, double turnShare) {
        return StreamCone.launchDirection(aim, SPRAY_DEGREES, tiltShare, turnShare).scale(LAUNCH_SPEED);
    }

    /**
     * The gnat's velocity after one tick of seeking.
     *
     * @param velocity the gnat's velocity, blocks per tick
     * @param at       where the gnat is
     * @param prey     where its prey is this tick
     * @return the new velocity
     */
    public static Vec3 steer(Vec3 velocity, Vec3 at, Vec3 prey) {
        Vec3 toPrey = prey.subtract(at);
        double gap = toPrey.length();
        if (gap * gap < ON_THE_PREY_SQUARED) {
            return velocity.scale(1 - TURN_SHARE);
        }
        Vec3 straightAtIt = gap < CRUISE ? toPrey : toPrey.scale(CRUISE / gap);
        return velocity.add(straightAtIt.subtract(velocity).scale(TURN_SHARE));
    }
}
