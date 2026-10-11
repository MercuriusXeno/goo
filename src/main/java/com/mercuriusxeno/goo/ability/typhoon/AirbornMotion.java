package com.mercuriusxeno.goo.ability.typhoon;

import net.minecraft.world.phys.Vec3;

/**
 * What Airborn does to a player's motion each tick, apart from the world:
 * off the ground, movement input turns the horizontal velocity a share of the
 * way toward the input's direction at the air speed, levitation included, so
 * the player changes direction in the air with ease without being slowed; a
 * fall never passes the cap; Jet pushes harder; and an elytra glide is drawn
 * along the look toward the glide speed.
 * airborn-steerable-levitation-and-soft-falls
 */
public final class AirbornMotion {

    private static final double NO_INPUT_SQUARED = 1e-6;

    private AirbornMotion() {
    }

    /**
     * The player's velocity after one tick of Airborn.
     *
     * @param velocity the velocity now
     * @param input    the movement input as vanilla reads it: strafe left as x, forward as z, each -1 to 1
     * @param yaw      the player's yaw in degrees
     * @param onGround whether the player stands on the ground
     * @param airborn  the Airborn standing
     * @return the velocity steered, if airborne, and its fall capped
     */
    public static Vec3 moved(Vec3 velocity, Vec3 input, float yaw, boolean onGround, Airborn airborn) {
        Vec3 steered = onGround ? velocity : steered(velocity, input, yaw, airborn);
        return new Vec3(steered.x, cappedFall(steered.y, airborn.fallCap()), steered.z);
    }

    /**
     * A midair velocity turned toward the input's direction: the horizontal
     * part moves the steer share of the way toward the input's direction at
     * the air speed or the speed it already has, whichever is faster, so
     * steering never brakes a faster flight such as Jet's; the vertical part,
     * levitation's or a fall's, stays. With no input the velocity stays.
     *
     * @param velocity the velocity now
     * @param input    the movement input: strafe left as x, forward as z
     * @param yaw      the player's yaw in degrees
     * @param airborn  the Airborn standing
     * @return the steered velocity
     */
    static Vec3 steered(Vec3 velocity, Vec3 input, float yaw, Airborn airborn) {
        Vec3 heading = heading(input, yaw);
        if (heading.lengthSqr() < NO_INPUT_SQUARED) {
            return velocity;
        }
        Vec3 current = new Vec3(velocity.x, 0, velocity.z);
        // airborn-steerable-levitation-and-soft-falls: faster flight, steering keeps Jet's speed rather than braking it
        double speed = Math.max(airborn.airSpeed(), current.length());
        Vec3 horizontal = current.lerp(heading.normalize().scale(speed), Math.clamp(airborn.airSteer(), 0f, 1f));
        return new Vec3(horizontal.x, velocity.y, horizontal.z);
    }

    /**
     * The world direction movement input points at a yaw, as vanilla turns
     * the input: forward is the facing, strafe is to its side.
     *
     * @param input the movement input: strafe left as x, forward as z
     * @param yaw   the yaw in degrees
     * @return the horizontal direction, unnormalized
     */
    static Vec3 heading(Vec3 input, float yaw) {
        double radians = Math.toRadians(yaw);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        return new Vec3(input.x * cos - input.z * sin, 0, input.z * cos + input.x * sin);
    }

    /**
     * A vertical speed whose fall never passes the cap; a rise stays.
     *
     * @param verticalSpeed the vertical speed, negative while falling
     * @param fallCap       the fastest fall, in blocks per tick
     * @return the capped speed
     */
    static double cappedFall(double verticalSpeed, double fallCap) {
        return Math.max(verticalSpeed, -fallCap);
    }

    /**
     * An elytra glide's velocity after one tick of Airborn: slower than the
     * glide speed, it moves the glide steer share of the way toward the look
     * at that speed, as a gentle firework would; at or past it, it stays.
     *
     * @param velocity the velocity now
     * @param look     the player's look, a unit vector
     * @param airborn  the Airborn standing
     * @return the drawn velocity
     */
    public static Vec3 glided(Vec3 velocity, Vec3 look, Airborn airborn) {
        if (velocity.length() >= airborn.glideSpeed()) {
            return velocity;
        }
        return velocity.lerp(look.scale(airborn.glideSpeed()), Math.clamp(airborn.glideSteer(), 0f, 1f));
    }

    /**
     * Jet's push strength for a player: multiplied by Airborn's jet boost
     * while it stands.
     *
     * @param strength the push's own strength
     * @param airborn  the player's Airborn
     * @param gameTime the game time
     * @return the strength the push drives at
     */
    public static double jetStrength(double strength, Airborn airborn, long gameTime) {
        return airborn.standsAt(gameTime) ? strength * airborn.jetBoost() : strength;
    }
}
