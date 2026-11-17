package com.mercuriusxeno.goo.ability.crystal;

import com.mercuriusxeno.goo.throwing.ProjectileFlight;
import net.minecraft.world.phys.Vec3;

/**
 * A thrown knife or javelin, shared by the server that strikes with it and
 * the client that draws it: the one formed last leaves its hover slot first,
 * along the thrower's look at its delivery's throw speed, a knife 1.5 blocks
 * a tick and a javelin 2.5, and drops like an arrow, seeking no mob.
 * decision knives-and-javelins-drop-like-an-arrow
 * decision knives-do-not-seek-you-just-throw-them
 * decision dart-does-not-seek-or-highlight
 */
public final class ThrownKnife {

    /** The most ticks a knife or javelin flies before it is spent. */
    public static final int MOST_FLIGHT_TICKS = 100;

    private ThrownKnife() {
    }

    /**
     * Where the one a throw takes leaves from: the hover slot of the one
     * formed last, over the cube at the steady point by the glove.
     * decision knives-hover-above-the-blob-cube-over-the-hand
     *
     * @param eye        the thrower's eye
     * @param xRot       the thrower's view pitch in degrees
     * @param yRot       the thrower's view yaw in degrees
     * @param gloveRight whether the glove is in the right hand
     * @param held       the count held as the throw leaves, at least 1
     * @param javelins   whether the stack is Dart's javelins rather than Knives' knives
     * @return the point
     */
    public static Vec3 launchPointOf(Vec3 eye, float xRot, float yRot, boolean gloveRight, int held,
                                     boolean javelins) {
        return StackHover.pointOf(StackHover.cubeCenterInView(eye, xRot, yRot, gloveRight), xRot, yRot,
                StackHover.slotOf(held - 1, javelins));
    }

    /**
     * The velocity a throw leaves with.
     *
     * @param look  the thrower's look, any length
     * @param speed the delivery's throw speed, blocks per tick
     * @return the velocity, blocks per tick
     */
    public static Vec3 launchVelocityAlong(Vec3 look, double speed) {
        return ProjectileFlight.launch(look, speed);
    }
}
