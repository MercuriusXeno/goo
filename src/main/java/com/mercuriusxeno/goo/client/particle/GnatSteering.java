package com.mercuriusxeno.goo.client.particle;

import com.mercuriusxeno.goo.throwing.StreamCone;
import net.minecraft.world.phys.Vec3;

/**
 * How a seeking gnat flies so it reads as homing rather than auto-aim: it
 * leaves the glove along the line the player fires, flies straight down it
 * for its first ticks, then veers, each tick turning its heading a share of
 * the way toward the prey at a steady speed, so its path curves off the aim
 * into the mob; it slows as it arrives so it settles on the mob rather than
 * overshooting (decision decay-gnats-degrade-each-block-once).
 */
public final class GnatSteering {

    /** A seeking gnat's speed, blocks per tick. */
    public static final double CRUISE = 0.35;
    /** Ticks a gnat flies straight down the firing line before it veers. */
    public static final int STRAIGHT_TICKS = 4;
    /** The spray a gnat launches inside, apex to rim, so the widest leaves 5 degrees off the firing line. */
    private static final double SPRAY_DEGREES = 10;
    /** The share of the way toward a straight heading a gnat turns on its first veering tick. */
    private static final double TURN_SHARE = 0.15;
    /** How much sharper a gnat turns each tick it keeps veering. */
    private static final double TURN_TIGHTENING = 0.02;
    /** The sharpest a gnat ever turns. */
    private static final double TIGHTEST_TURN_SHARE = 0.5;
    /** Within this many blocks of its prey a gnat slows to settle on it. */
    private static final double ARRIVING_WITHIN = 1.5;
    /** Below this squared gap a gnat sits on its prey and has no heading to take. */
    private static final double ON_THE_PREY_SQUARED = 1e-6;

    private GnatSteering() {
    }

    /**
     * A seeking gnat's launch from the glove.
     *
     * @param aim       the heading from the glove onto the crosshair, any length
     * @param tiltShare how far off the firing line, 0 on it and 1 at the spray's rim
     * @param turnShare which way round the firing line, a share of a full turn
     * @return the launch velocity, blocks per tick
     */
    public static Vec3 launch(Vec3 aim, double tiltShare, double turnShare) {
        return StreamCone.launchDirection(aim, SPRAY_DEGREES, tiltShare, turnShare).scale(CRUISE);
    }

    /**
     * The gnat's velocity after one tick of seeking.
     *
     * @param velocity the gnat's velocity, blocks per tick
     * @param at       where the gnat is
     * @param prey     where its prey is this tick
     * @param age      the ticks the gnat has flown
     * @return the new velocity
     */
    public static Vec3 steer(Vec3 velocity, Vec3 at, Vec3 prey, int age) {
        if (age < STRAIGHT_TICKS) {
            return velocity;
        }
        Vec3 toPrey = prey.subtract(at);
        double gap = toPrey.length();
        double share = turnShareAt(age);
        if (gap * gap < ON_THE_PREY_SQUARED) {
            return velocity.scale(1 - share);
        }
        Vec3 straightAtIt = toPrey.scale(CRUISE * Math.min(1, gap / ARRIVING_WITHIN) / gap);
        Vec3 turned = velocity.add(straightAtIt.subtract(velocity).scale(share));
        return gap < ARRIVING_WITHIN ? turned : turned.normalize().scale(CRUISE);
    }

    /**
     * How sharply a veering gnat turns: gently on its first veer, tightening
     * every tick after, so a gnat that swung wide still lands on its prey
     * instead of circling it.
     *
     * @param age the ticks the gnat has flown, past its straight run
     * @return the share of the way toward a straight heading it turns this tick
     */
    private static double turnShareAt(int age) {
        return Math.min(TIGHTEST_TURN_SHARE, TURN_SHARE + TURN_TIGHTENING * (age - STRAIGHT_TICKS));
    }
}
