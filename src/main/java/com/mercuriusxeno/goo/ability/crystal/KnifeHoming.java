package com.mercuriusxeno.goo.ability.crystal;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A thrown knife's homing flight, shared by the server that strikes with it
 * and the client that draws it: it leaves its fan slot at 1.5 blocks a tick,
 * a snowball's speed, with no gravity, and each tick its velocity turns
 * toward where its mob stands now, so it reaches the mob though the mob moves.
 * decision knives-home-on-their-painted-mob
 * decision knives-leave-the-blob-at-a-snowballs-speed
 */
public final class KnifeHoming {

    /** A knife's speed, in blocks a tick: a snowball's. */
    public static final double SPEED = 1.5;
    /** The most ticks a knife flies before it is spent. */
    public static final int MOST_FLIGHT_TICKS = 100;

    private KnifeHoming() {
    }

    /**
     * A knife's velocity this tick: the knife's speed toward its mob, or
     * the step that lands it there where the mob stands nearer than that.
     *
     * @param position where the knife is
     * @param aim      where its mob is now
     * @return the velocity, in blocks a tick
     */
    public static Vec3 velocityToward(Vec3 position, Vec3 aim) {
        Vec3 toward = aim.subtract(position);
        double distance = toward.length();
        return distance <= SPEED ? toward : toward.scale(SPEED / distance);
    }

    /**
     * Whether a knife's step reaches its mob: the step passes through the
     * mob's box or ends inside it.
     *
     * @param from where the step starts
     * @param to   where it stops
     * @param mob  the mob's box
     * @return true where the step meets the box
     */
    public static boolean arrives(Vec3 from, Vec3 to, AABB mob) {
        return mob.contains(to) || mob.clip(from, to).isPresent();
    }
}
