package com.mercuriusxeno.goo.ability.crystal;

import net.minecraft.world.phys.Vec3;

/**
 * Knives' glove hand and the blob floating ahead of it, shared by the
 * server and the client that draws them.
 * decision knives-sling-then-morph-to-flechettes
 * decision knives-blob-floats-ahead-of-the-hand-and-spews-the-knives
 */
public final class KnifeRain {

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

    private KnifeRain() {
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
}
