package com.mercuriusxeno.goo.ability.crystal;

import com.mercuriusxeno.goo.throwing.ProjectileFlight;
import net.minecraft.world.phys.Vec3;

/**
 * A thrown knife, shared by the server that strikes with it and the client
 * that draws it: the knife formed last leaves its hover slot first, along the
 * thrower's look at 1.5 blocks a tick, and drops like an arrow, seeking no mob.
 * decision knives-and-javelins-drop-like-an-arrow
 * decision knives-do-not-seek-you-just-throw-them
 */
public final class ThrownKnife {

    /** A knife's speed as it leaves, in blocks a tick. */
    public static final double SPEED = 1.5;
    /** The most ticks a knife flies before it is spent. */
    public static final int MOST_FLIGHT_TICKS = 100;

    private ThrownKnife() {
    }

    /**
     * Where the knife a throw takes leaves from: the hover slot of the knife
     * formed last.
     *
     * @param eye  the thrower's eye
     * @param look the thrower's look, a unit vector
     * @param held the knives held as the throw leaves, at least 1
     * @return the point
     */
    public static Vec3 launchPointOf(Vec3 eye, Vec3 look, int held) {
        return KnifeFan.pointOf(eye, look, KnifeFan.slotOf(held - 1, held));
    }

    /**
     * The velocity a knife leaves with.
     *
     * @param look the thrower's look, any length
     * @return the velocity, blocks per tick
     */
    public static Vec3 launchVelocityAlong(Vec3 look) {
        return ProjectileFlight.launch(look, SPEED);
    }
}
