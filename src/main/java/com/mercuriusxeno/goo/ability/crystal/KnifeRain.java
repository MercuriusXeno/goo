package com.mercuriusxeno.goo.ability.crystal;

import com.mercuriusxeno.goo.ability.Charge;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The flight of Knives' glass knives, shared by the server that strikes
 * with them and the client that draws them: each knife leaves the blob
 * floating ahead of the hand at its own tick, aimed into its own slice of
 * the cone with a scatter of yaw, pitch and speed, and arcs under gravity,
 * so the cloud rains into an area rather than flying out as a line.
 * decision knives-sling-then-morph-to-flechettes
 * decision knives-blob-floats-ahead-of-the-hand-and-spews-the-knives
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
    /** Blocks the wind-up eases the hand across the chest at its furthest, past the body's middle. */
    private static final double HAND_ACROSS = 0.45;
    /**
     * Blocks along the look and up the floating blob stands from the hand.
     * First values, for the operator to tune.
     */
    private static final double FLOAT_AHEAD = 0.15;
    private static final double FLOAT_ABOVE = 0.2;
    /** Ticks the wind-up takes to ease the hand across the chest: Knives' full charge. */
    public static final float WIND_UP_TICKS = 30f;
    /** The smoothstep's terms: three times the square less twice the cube. */
    private static final float SMOOTH_SQUARE = 3f;
    private static final float SMOOTH_CUBE = 2f;
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
            velocities.add(Vec3.directionFromRotation((float) (pitch - LIFT_DEGREES + pitchOffset),
                    (float) (yaw + yawOffset)).scale(speed));
        }
        return velocities;
    }

    /**
     * Each knife's velocity leaving the hand for a release at a share of the
     * charge: the one scatter the server slings and the client's aim lines draw.
     * knives-aims-a-spread-fan-not-a-blob-line
     *
     * @param yaw    the thrower's yaw, in degrees
     * @param pitch  the thrower's pitch, in degrees
     * @param charge the delivery's charge
     * @param share  the share of a full charge the hold reached, 0 to 1
     * @param random the scatter's source
     * @return a velocity per knife, in blocks a tick
     */
    public static List<Vec3> releaseVelocities(float yaw, float pitch, Charge charge, float share,
                                               RandomSource random) {
        return scatteredVelocities(yaw, pitch, charge.fleckCount(share), charge.spreadDegrees(share), random);
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
     * The glove hand's point as the wind-up eases it across the chest: from
     * the glove side toward the other side as the charge is held. The blob
     * the knives leave from floats ahead of and above it.
     *
     * @param eye        the thrower's eye
     * @param yaw        the thrower's yaw, in degrees
     * @param look       the thrower's look, a unit vector
     * @param gloveRight whether the glove is in the right hand
     * @param across     how far the hand has eased across the chest, 0 at the glove side to 1 at its furthest
     * @return the point
     */
    public static Vec3 handPoint(Vec3 eye, float yaw, Vec3 look, boolean gloveRight, float across) {
        Vec3 right = Vec3.directionFromRotation(0f, yaw + RIGHT_OF_LOOK);
        double side = HAND_SIDE - HAND_ACROSS * across;
        return eye.add(look.scale(HAND_REACH)).add(right.scale(gloveRight ? side : -side))
                .subtract(0, HAND_DROP, 0);
    }

    /**
     * Where the blob lifted off the palm floats through Knives' charge:
     * ahead of the glove hand along the look and above it, moving with the
     * hand as it eases across the chest.
     * knives-blob-floats-ahead-of-the-hand-and-spews-the-knives
     *
     * @param eye        the thrower's eye
     * @param yaw        the thrower's yaw, in degrees
     * @param look       the thrower's look, a unit vector
     * @param gloveRight whether the glove is in the right hand
     * @param across     how far the hand has eased across the chest, 0 at the glove side to 1 at its furthest
     * @return the point
     */
    public static Vec3 floatPoint(Vec3 eye, float yaw, Vec3 look, boolean gloveRight, float across) {
        return handPoint(eye, yaw, look, gloveRight, across).add(look.scale(FLOAT_AHEAD)).add(0, FLOAT_ABOVE, 0);
    }

    /**
     * How far a charge held some ticks has eased the glove hand across the
     * chest: slow at both ends, at its furthest once the wind-up is done.
     * knives-blob-floats-ahead-of-the-hand-and-spews-the-knives
     *
     * @param heldTicks the ticks the use key has been held
     * @return the share across, 0 at the glove side to 1 at its furthest
     */
    public static float windUpAcross(double heldTicks) {
        float share = (float) Math.clamp(heldTicks / WIND_UP_TICKS, 0.0, 1.0);
        return share * share * (SMOOTH_SQUARE - SMOOTH_CUBE * share);
    }

    /**
     * Where Knives' knives leave from, the blob floating ahead of the hand:
     * the float point at the hand's reach across the chest when the charge
     * was let go, the one point the server launches each knife from and the
     * client roots each aim line at. Read at a knife's own launch tick, it
     * follows the look through the backhand.
     * knives-blob-floats-ahead-of-the-hand-and-spews-the-knives
     * knives-aims-a-spread-fan-not-a-blob-line
     *
     * @param eye        the thrower's eye
     * @param yaw        the thrower's yaw, in degrees
     * @param look       the thrower's look, a unit vector
     * @param gloveRight whether the glove is in the right hand
     * @param heldTicks  the ticks the charge was held
     * @return the point
     */
    public static Vec3 leavesFrom(Vec3 eye, float yaw, Vec3 look, boolean gloveRight, int heldTicks) {
        return floatPoint(eye, yaw, look, gloveRight, windUpAcross(heldTicks));
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
