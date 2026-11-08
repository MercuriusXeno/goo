package com.mercuriusxeno.goo.client.particle;

import net.minecraft.world.phys.Vec3;

/**
 * How a seeking gnat turns toward its prey each tick: its velocity eases a
 * share of the way toward the velocity that heads straight at the prey at
 * cruising speed, so a gnat launched along the aim bends round onto a mob off
 * the aim instead of snapping onto it, and slows as it arrives so it settles
 * on the mob rather than overshooting (decision decay-gnats-degrade-each-block-once).
 */
public final class GnatSteering {

    /** A seeking gnat's speed, blocks per tick. */
    public static final double CRUISE = 0.35;
    /** The share of the way toward a straight heading a gnat turns each tick. */
    private static final double TURN_SHARE = 0.2;
    /** Below this squared gap a gnat sits on its prey and has no heading to take. */
    private static final double ON_THE_PREY_SQUARED = 1e-6;

    private GnatSteering() {
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
