package com.mercuriusxeno.goo.ability.crystal;

import com.mercuriusxeno.goo.ability.Charge;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * The flight of Knives' glass knives, shared by the server that strikes
 * with them and the client that draws them: each knife leaves the blob
 * floating ahead of the hand at its own even tick, aimed down its own even
 * slice of the cone at its row's speed, and arcs under gravity, so the
 * cloud rains into an area in staggered rows rather than flying out as a line.
 * decision knives-sling-then-morph-to-flechettes
 * decision knives-blob-floats-ahead-of-the-hand-and-spews-the-knives
 * decision knives-land-in-staggered-rows-with-no-randomness
 */
public final class KnifeRain {

    /** Blocks per tick squared a knife falls by. */
    public static final double GRAVITY = 0.05;
    /** The share of its speed a knife keeps each tick. */
    public static final double DRAG = 0.99;
    /** The most ticks a knife flies before it is gone. */
    public static final int MOST_FLIGHT_TICKS = 60;
    /**
     * A knife's speed leaving the hand, in blocks a tick: a snowball's.
     * decision knives-leave-the-blob-at-a-snowballs-speed
     */
    static final double SPEED = 1.5;
    /** The share of its speed a knife's row steps it by, slower for the near row, faster for the far. */
    static final double ROW_SPEED_STEP = 0.2;
    /** The rows by place from the thrower's left, in steps of speed: near, far, middle. */
    private static final int[] ROW_SPEED_STEPS = {-1, 1, 0};
    /** Degrees above the look the cloud is lobbed, so it arcs. */
    static final float LIFT_DEGREES = 8f;
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
    /** Ticks the backhand takes to sweep the hand out, holds it flung, and takes to bring it back. */
    public static final float BACKHAND_OUT_TICKS = 3f;
    public static final float BACKHAND_HOLD_TICKS = 3f;
    public static final float BACKHAND_RETURN_TICKS = 5f;
    /**
     * Where the flung hand stands across the chest: out past the glove side,
     * about seven tenths as far as the wind-up carries it in, as the
     * first-person fling sets the hand out against the wind-up.
     */
    private static final float FLUNG_ACROSS = -0.7f;
    /** The smoothstep's terms: three times the square less twice the cube. */
    private static final float SMOOTH_SQUARE = 3f;
    private static final float SMOOTH_CUBE = 2f;
    /** Degrees from the look to the thrower's right, around the vertical. */
    private static final float RIGHT_OF_LOOK = 90f;
    private static final double HALF = 0.5;

    private KnifeRain() {
    }

    /**
     * Each knife's velocity leaving the hand: the cone cut into one even
     * slice a knife from the thrower's left to right, each knife aimed down
     * its slice's middle, and its speed stepping near, far, middle by its
     * place, so the knives land in three rows offset like brickwork.
     * knives-land-in-staggered-rows-with-no-randomness
     *
     * @param yaw           the thrower's yaw, in degrees
     * @param pitch         the thrower's pitch, in degrees, negative looking up
     * @param count         the knives
     * @param spreadDegrees the cone, edge to edge, in degrees
     * @return a velocity per knife, in blocks a tick, leftmost first
     */
    public static List<Vec3> patternVelocities(float yaw, float pitch, int count, double spreadDegrees) {
        List<Vec3> velocities = new ArrayList<>(count);
        double slice = spreadDegrees / Math.max(1, count);
        for (int index = 0; index < count; index++) {
            double yawOffset = -spreadDegrees * HALF + slice * (index + HALF);
            double speed = SPEED * (1 + ROW_SPEED_STEPS[index % ROW_SPEED_STEPS.length] * ROW_SPEED_STEP);
            velocities.add(direction(pitch - LIFT_DEGREES, yaw + yawOffset).scale(speed));
        }
        return velocities;
    }

    /**
     * The unit vector a pitch and a yaw face, as {@link Vec3#directionFromRotation}
     * reads them but in full precision, since its sine table would skew the
     * pattern's even spacing.
     *
     * @param pitchDegrees the pitch, negative looking up
     * @param yawDegrees   the yaw, rising to the right
     * @return the direction
     */
    private static Vec3 direction(double pitchDegrees, double yawDegrees) {
        double pitch = Math.toRadians(pitchDegrees);
        double yaw = Math.toRadians(yawDegrees);
        double level = Math.cos(pitch);
        return new Vec3(-Math.sin(yaw) * level, -Math.sin(pitch), Math.cos(yaw) * level);
    }

    /**
     * Each knife's velocity leaving the hand for a release at a share of the
     * charge: the pattern the server slings.
     *
     * @param yaw    the thrower's yaw, in degrees
     * @param pitch  the thrower's pitch, in degrees
     * @param charge the delivery's charge
     * @param share  the share of a full charge the hold reached, 0 to 1
     * @return a velocity per knife, in blocks a tick, leftmost first
     */
    public static List<Vec3> releaseVelocities(float yaw, float pitch, Charge charge, float share) {
        return patternVelocities(yaw, pitch, charge.fleckCount(share), charge.spreadDegrees(share));
    }

    /**
     * The tick after the release each knife leaves the hand: even steps
     * across the sweep, from the first tick to its last, so the knives
     * stream out rather than leave together.
     * knives-land-in-staggered-rows-with-no-randomness
     *
     * @param count      the knives
     * @param sweepTicks the ticks between the first knife and the last
     * @return a launch tick per knife, in the order the velocities run
     */
    public static int[] launchTicks(int count, int sweepTicks) {
        int[] ticks = new int[count];
        for (int index = 0; index < count; index++) {
            ticks[index] = count <= 1 ? 0 : (int) Math.round((double) sweepTicks * index / (count - 1));
        }
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
        return smooth(heldTicks / WIND_UP_TICKS);
    }

    /**
     * How far across the chest the glove hand stands a number of ticks into
     * the backhand: sweeping out from where the wind-up got to, past the
     * glove side to where the arm flings, holding there, then coming back to
     * the glove side, as the arm's fling plays it.
     * knives-blob-floats-ahead-of-the-hand-and-spews-the-knives: the blob follows the hand through the backhand
     *
     * @param from         how far across the wind-up had eased the hand at the release, 0 to 1
     * @param sinceRelease the ticks since the release
     * @return the share across, negative out past the glove side
     */
    public static float backhandAcross(float from, double sinceRelease) {
        if (sinceRelease < BACKHAND_OUT_TICKS) {
            float out = smooth(sinceRelease / BACKHAND_OUT_TICKS);
            return from + (FLUNG_ACROSS - from) * out;
        }
        double returning = sinceRelease - BACKHAND_OUT_TICKS - BACKHAND_HOLD_TICKS;
        return FLUNG_ACROSS * (1f - smooth(returning / BACKHAND_RETURN_TICKS));
    }

    /**
     * An ease's progress, slow at both ends.
     *
     * @param share the share of the ease's ticks gone, clamped to 0 to 1
     * @return the eased share
     */
    private static float smooth(double share) {
        float clamped = (float) Math.clamp(share, 0.0, 1.0);
        return clamped * clamped * (SMOOTH_SQUARE - SMOOTH_CUBE * clamped);
    }

    /**
     * Where Knives' knives leave from, the blob floating ahead of the hand:
     * the float point at the hand's place across the chest that many ticks
     * into the backhand, from wherever the held charge's wind-up got to. The
     * server launches each knife from it at that knife's own launch tick, so
     * the blob spews them along the hand's path.
     * knives-blob-floats-ahead-of-the-hand-and-spews-the-knives
     *
     * @param eye          the thrower's eye
     * @param yaw          the thrower's yaw, in degrees
     * @param look         the thrower's look, a unit vector
     * @param gloveRight   whether the glove is in the right hand
     * @param heldTicks    the ticks the charge was held
     * @param sinceRelease the ticks since the release, 0 before it
     * @return the point
     */
    public static Vec3 leavesFrom(Vec3 eye, float yaw, Vec3 look, boolean gloveRight, int heldTicks,
                                  double sinceRelease) {
        return floatPoint(eye, yaw, look, gloveRight, backhandAcross(windUpAcross(heldTicks), sinceRelease));
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
}
