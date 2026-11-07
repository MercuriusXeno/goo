package com.mercuriusxeno.goo.ability.typhoon;

import net.minecraft.world.phys.Vec3;

/**
 * What Float does to a player's motion each tick, apart from the world: off
 * the ground jump climbs, sneak descends, forward and backward fly along the
 * look vector and left and right strafe, levitation included; a fall never
 * passes the cap; and an elytra glide is drawn along the look toward the
 * glide speed.
 * tailwind-and-airborn-merge-into-float
 */
public final class AirbornMotion {

    private static final double NO_INPUT_SQUARED = 1e-6;

    private AirbornMotion() {
    }

    /**
     * The player's velocity after one tick of Float's flight. In midair the
     * velocity turns the steer share of the way toward where the input flies:
     * forward and backward along the look vector and left and right across it
     * at the air speed, jump up and sneak down at the climb speed. Strafing
     * alone leaves the height to the fall; no input leaves the velocity; a
     * fall never passes the cap. On the ground vanilla moves the player.
     *
     * @param velocity the velocity now
     * @param input    the flight input: strafe left as x, jump up and sneak down as y, forward as z, each -1 to 1
     * @param look     the player's look, a unit vector
     * @param yaw      the player's yaw in degrees
     * @param onGround whether the player stands on the ground
     * @param flight   the Float standing
     * @return the velocity flown
     */
    public static Vec3 flown(Vec3 velocity, Vec3 input, Vec3 look, float yaw, boolean onGround, Airborn flight) {
        if (onGround) {
            return velocity;
        }
        Vec3 next = velocity;
        if (input.lengthSqr() >= NO_INPUT_SQUARED) {
            Vec3 aim = look.scale(input.z * flight.airSpeed())
                    .add(heading(new Vec3(input.x, 0, 0), yaw).scale(flight.airSpeed()))
                    .add(0, input.y * flight.climbSpeed(), 0);
            Vec3 steered = velocity.lerp(aim, Math.clamp(flight.airSteer(), 0f, 1f));
            boolean drivesHeight = input.y != 0 || input.z != 0;
            next = drivesHeight ? steered : new Vec3(steered.x, velocity.y, steered.z);
        }
        return new Vec3(next.x, cappedFall(next.y, flight.fallCap()), next.z);
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
}
