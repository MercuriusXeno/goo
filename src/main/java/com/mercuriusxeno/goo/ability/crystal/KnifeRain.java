package com.mercuriusxeno.goo.ability.crystal;

import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The flight of Knives' glass knives, shared by the server that strikes
 * with them and the client that draws them: each knife leaves the hand at
 * its own tick, aimed into its own slice of the cone with a scatter of
 * yaw, pitch and speed, and arcs under gravity, so the cloud rains into an
 * area rather than flying out as a line.
 * decision shards-sling-then-morph-to-flechettes
 */
public final class KnifeRain {

    /** Blocks per tick squared a knife falls by. */
    public static final double GRAVITY = 0.05;
    /** The share of its speed a knife keeps each tick. */
    public static final double DRAG = 0.99;
    /** The most ticks a knife flies before it is gone. */
    public static final int MOST_FLIGHT_TICKS = 60;
    /** A knife's speed leaving the hand, in blocks a tick. */
    static final double SPEED = 1.0;
    /** The share a knife's speed scatters by, either way. */
    static final double SPEED_SCATTER = 0.2;
    /** Degrees above the look the cloud is lobbed, so it arcs. */
    static final float LIFT_DEGREES = 8f;
    /** Degrees a knife's pitch scatters by, either way. */
    static final float PITCH_SCATTER_DEGREES = 7f;
    /** Blocks below the eye the knives leave from, the glove hand's height. */
    private static final double HAND_DROP = 0.3;
    /** Blocks toward the glove side the knives leave from. */
    private static final double HAND_SIDE = 0.35;
    /** Blocks ahead of the eye the knives leave from. */
    private static final double HAND_REACH = 0.4;
    /** Degrees from the look to the thrower's right, around the vertical. */
    private static final float RIGHT_OF_LOOK = 90f;
    private static final double HALF = 0.5;
    private static final double BOTH_WAYS = 2;

    private KnifeRain() {
    }

    /**
     * Each knife's velocity leaving the hand: the cone cut into one slice a
     * knife and each knife aimed somewhere in its slice, so the cloud
     * covers the cone without lining up, its pitch and speed scattered too.
     *
     * @param yaw           the thrower's yaw, in degrees
     * @param pitch         the thrower's pitch, in degrees, negative looking up
     * @param count         the knives
     * @param spreadDegrees the cone, edge to edge, in degrees
     * @param random        the scatter's source
     * @return a velocity per knife, in blocks a tick
     */
    public static List<Vec3> scatteredVelocities(float yaw, float pitch, int count, double spreadDegrees,
                                                 RandomSource random) {
        List<Vec3> velocities = new ArrayList<>(count);
        double slice = spreadDegrees / Math.max(1, count);
        for (int index = 0; index < count; index++) {
            double yawOffset = -spreadDegrees * HALF + slice * (index + random.nextDouble());
            double pitchOffset = scatter(random) * PITCH_SCATTER_DEGREES;
            double speed = SPEED * (1 + scatter(random) * SPEED_SCATTER);
            velocities.add(velocityOf(yaw, pitch, yawOffset, pitchOffset, speed));
        }
        return velocities;
    }

    /**
     * The tick after the release each knife leaves the hand: spread across
     * the sweep with a little scatter, so the knives stream out rather than
     * leave together.
     *
     * @param count      the knives
     * @param sweepTicks the ticks between the first knife and the last
     * @param random     the scatter's source
     * @return a launch tick per knife, in the order the velocities run
     */
    public static int[] launchTicks(int count, int sweepTicks, RandomSource random) {
        int[] ticks = new int[count];
        for (int index = 0; index < count; index++) {
            double even = count <= 1 ? 0 : (double) sweepTicks * index / (count - 1);
            ticks[index] = (int) Math.clamp(Math.round(even + scatter(random)), 0, sweepTicks);
        }
        Arrays.sort(ticks);
        return ticks;
    }

    /**
     * The glove hand's point, where the knives leave from: the one point the
     * server launches each knife from and the client roots the fan at.
     * knives-aims-a-spread-fan-not-a-blob-line
     *
     * @param eye        the thrower's eye
     * @param yaw        the thrower's yaw, in degrees
     * @param look       the thrower's look, a unit vector
     * @param gloveRight whether the glove is in the right hand
     * @return the point
     */
    public static Vec3 handPoint(Vec3 eye, float yaw, Vec3 look, boolean gloveRight) {
        Vec3 right = Vec3.directionFromRotation(0f, yaw + RIGHT_OF_LOOK);
        return eye.add(look.scale(HAND_REACH)).add(right.scale(gloveRight ? HAND_SIDE : -HAND_SIDE))
                .subtract(0, HAND_DROP, 0);
    }

    /**
     * A knife's velocity leaving the hand at a yaw offset from the look,
     * with no scatter: the line a knife at that offset flies on average.
     *
     * @param yaw       the thrower's yaw, in degrees
     * @param pitch     the thrower's pitch, in degrees, negative looking up
     * @param yawOffset degrees from the look, positive to the right
     * @return the velocity, in blocks a tick
     */
    public static Vec3 aimedVelocity(float yaw, float pitch, double yawOffset) {
        return velocityOf(yaw, pitch, yawOffset, 0, SPEED);
    }

    /**
     * A knife's velocity leaving the hand, lobbed above the look.
     *
     * @param yaw         the thrower's yaw, in degrees
     * @param pitch       the thrower's pitch, in degrees
     * @param yawOffset   degrees from the look's yaw
     * @param pitchOffset degrees from the lobbed pitch
     * @param speed       blocks a tick
     * @return the velocity
     */
    private static Vec3 velocityOf(float yaw, float pitch, double yawOffset, double pitchOffset, double speed) {
        return Vec3.directionFromRotation((float) (pitch - LIFT_DEGREES + pitchOffset), (float) (yaw + yawOffset))
                .scale(speed);
    }

    /**
     * Where a knife is a number of ticks into its flight.
     *
     * @param start    where it left the hand
     * @param velocity its velocity leaving the hand
     * @param ticks    ticks into the flight
     * @return its position
     */
    public static Vec3 positionAt(Vec3 start, Vec3 velocity, int ticks) {
        Vec3 position = start;
        Vec3 moving = velocity;
        for (int tick = 0; tick < ticks; tick++) {
            position = position.add(moving);
            moving = nextVelocity(moving);
        }
        return position;
    }

    /**
     * A knife's velocity a number of ticks into its flight.
     *
     * @param velocity its velocity leaving the hand
     * @param ticks    ticks into the flight
     * @return its velocity then
     */
    public static Vec3 velocityAt(Vec3 velocity, int ticks) {
        Vec3 moving = velocity;
        for (int tick = 0; tick < ticks; tick++) {
            moving = nextVelocity(moving);
        }
        return moving;
    }

    /**
     * A knife's velocity a tick on: slowed by the air and pulled down.
     *
     * @param velocity its velocity now
     * @return its velocity a tick on
     */
    public static Vec3 nextVelocity(Vec3 velocity) {
        return velocity.scale(DRAG).subtract(0, GRAVITY, 0);
    }

    /**
     * A scatter either way.
     *
     * @param random the scatter's source
     * @return a value in [-1, 1)
     */
    private static double scatter(RandomSource random) {
        return (random.nextDouble() - HALF) * BOTH_WAYS;
    }
}
