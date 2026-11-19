package com.mercuriusxeno.goo.ability.nether;

import net.minecraft.world.phys.AABB;
import java.util.stream.DoubleStream;

/**
 * How a body under Invert meets the ceiling: the ceiling is its ground, so a
 * move it asks for toward the ceiling that the ceiling stops grounds it, and
 * every other move leaves it falling. Each of vanilla's readings of the
 * ground below the feet mirrors to the ceiling above the body's top: the
 * jump, the fall's distance, the block walked on, the step and the edge a
 * sneak holds at.
 * invert-flips-your-gravity-and-lets-you-walk-on-ceilings
 */
public final class CeilingGround {

    /** Whether a body rests on its ground after a move, or falls. */
    public enum Contact {
        /** The move ended against the side gravity pulls toward. */
        GROUNDED,
        /** The move left the body in the air. */
        FALLING
    }

    /** Half a box's height, the line an eye crosses between an upright body's and one on its ceiling. */
    private static final float HALF = 0.5F;

    private CeilingGround() {
    }

    /**
     * Answers whether a move left a body grounded.
     *
     * @param verticalCollision whether a block stopped the move's vertical part
     * @param askedY            the vertical part of the move asked for, before collision
     * @param inverted          whether the body's gravity is turned over
     * @return GROUNDED where the move pressed into the side gravity pulls toward and was stopped there
     */
    public static Contact contact(boolean verticalCollision, double askedY, boolean inverted) {
        return verticalCollision && towardGround(askedY, inverted) < 0 ? Contact.GROUNDED : Contact.FALLING;
    }

    /**
     * A vertical move as the body reads it, negative toward its ground: vanilla
     * counts a fall's distance from moves down, so an inverted body's moves up
     * count instead.
     *
     * @param dy       the vertical move
     * @param inverted whether the body's gravity is turned over
     * @return the move, negated for an inverted body
     */
    public static double towardGround(double dy, boolean inverted) {
        return inverted ? -dy : dy;
    }

    /**
     * The vertical speed a jump leaves: away from the ground at the jump's
     * power, or faster where the body already moves away faster.
     *
     * @param jumpPower the jump's power
     * @param currentY  the body's vertical speed
     * @param inverted  whether the body's gravity is turned over
     * @return the vertical speed after the jump
     */
    public static double jumpSpeed(double jumpPower, double currentY, boolean inverted) {
        return inverted ? Math.min(-jumpPower, currentY) : Math.max(jumpPower, currentY);
    }

    /**
     * The y of the block an inverted body reads as under its feet at an
     * offset into its ground, the ceiling block over its top.
     *
     * @param bodyTop the top of the body's bounding box
     * @param offset  how far into the ground the reading reaches
     * @return the block y
     */
    public static int groundBlockY(double bodyTop, float offset) {
        return (int) Math.floor(bodyTop + offset);
    }

    /**
     * Mirrors a probe vanilla reads under a body's feet to over its top: the
     * same width and depth, standing as high over the top as it reached under
     * the bottom.
     *
     * @param probe the probe under the body
     * @param body  the body's bounding box
     * @return the probe over the body
     */
    public static AABB mirrorAbove(AABB probe, AABB body) {
        return new AABB(probe.minX, body.maxY + (body.minY - probe.maxY), probe.minZ,
                probe.maxX, body.maxY + (body.minY - probe.minY), probe.maxZ);
    }

    /**
     * The heights an inverted body may step down from its ceiling to walk on
     * under a block hanging from it, shortest first: each bottom face of a
     * collider within the step height under the body's top, less the height
     * the move already made.
     *
     * @param bodyTop     the top of the body's bounding box, standing on its ceiling
     * @param colliderYs  the y of every face of each collider in reach
     * @param maxStep     the body's step height
     * @param alreadyMade the height the collided move already stepped
     * @return the step heights, ascending and distinct
     */
    public static float[] stepHeights(double bodyTop, DoubleStream colliderYs, float maxStep, float alreadyMade) {
        return toFloats(colliderYs.map(y -> (float) (bodyTop - y))
                .filter(height -> height >= 0 && height <= maxStep && (float) height != alreadyMade)
                .distinct().sorted().toArray());
    }

    /**
     * The eye height of a body standing on its ceiling: as far from the box's
     * bottom as an upright body's eye stands from its top.
     *
     * @param height    the box's height
     * @param eyeHeight the upright eye height
     * @return the eye height from the box's bottom
     */
    public static float eyeHeight(float height, float eyeHeight) {
        return height - eyeHeight;
    }

    /**
     * Answers whether a body's eye stands for one on its ceiling, in the lower
     * half of its box.
     *
     * @param height    the box's height
     * @param eyeHeight the eye height
     * @return true for an eye placed for the ceiling
     */
    public static boolean eyeOnTheCeilingSide(float height, float eyeHeight) {
        return eyeHeight < height * HALF;
    }

    private static float[] toFloats(double[] values) {
        float[] floats = new float[values.length];
        for (int i = 0; i < values.length; i++) {
            floats[i] = (float) values[i];
        }
        return floats;
    }
}
