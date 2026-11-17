package com.mercuriusxeno.goo.ability.crystal;

import com.mercuriusxeno.goo.ability.GloveRest;
import net.minecraft.world.phys.Vec3;

/**
 * Where a held stack hovers over its blob cube, shared by the server that
 * throws each one from its slot and the client that draws it. The cube rests
 * at the steady point by the glove and shrinks a step for each one held,
 * gone once the stack is full. Knives' ten knives hover in an upside-down
 * pyramid over the hand, rows of 4, 3, 2 and 1 from the top down, each row
 * centered over the cube, filling the top row left to right first and ending
 * on the single slot nearest the cube; each row stands just clear of the
 * cube's top at the count its first knife forms at, so the full cube sits
 * just under the top row and the cube has shrunk clear of each next row by
 * the time the row above it fills. Dart's three javelins hover side by side
 * at one height just clear of the full cube. Each one spawns at the cube's top.
 * decision knives-hover-in-an-upside-down-bowling-pin-pyramid
 * decision knives-hover-above-the-blob-cube-over-the-hand
 * decision knives-blob-shrinks-per-knife-and-vanishes-on-the-last
 * decision knives-cube-shrinks-clear-of-each-next-layer
 * decision dart-javelins-hover-over-the-shrinking-blob-cube
 */
public final class StackHover {

    /** Blocks a knife stands above and below its slot's height. */
    public static final double KNIFE_HALF_HEIGHT = 0.012;
    /** Blocks a javelin stands above and below its slot's height. */
    public static final double JAVELIN_HALF_HEIGHT = 0.02;
    /**
     * The full cube's edge, in blocks: the lifted palm blob's greatest drawn edge,
     * twice the flight shell's 0.15 half width at the palm rest's half flight size
     * and the lifted beat's greatest 0.9, so the stack stands over the cube drawn.
     */
    static final double FULL_EDGE = 2 * 0.15 * 0.5 * 0.9;
    /** Blocks between the cube's top and the lowest point of the row standing over it. */
    static final double CLEARANCE = 0.03;
    /** Blocks between neighbouring knives along a row. */
    static final double KNIFE_SPACING = 0.09;
    /** Blocks between neighbouring javelins. */
    static final double JAVELIN_SPACING = 0.1;
    /** The knives the pyramid holds. */
    public static final int KNIVES = 10;
    /** The javelins the row holds. */
    public static final int JAVELINS = 3;
    /** The knives in the pyramid's rows, the top row first. */
    private static final int[] ROW_WIDTHS = {4, 3, 2, 1};
    private static final double HALF = 0.5;
    private static final float QUARTER_TURN = 90f;

    private StackHover() {
    }

    /**
     * A slot in the cube's view frame.
     *
     * @param right blocks right of the cube's center, across the view
     * @param up    blocks above the cube's center, up the view
     */
    public record Slot(double right, double up) {
    }

    /**
     * A knife's slot in the pyramid: the top row's four left to right, then
     * the three, the two and the one nearest the cube.
     *
     * @param index the knife's index, 0 the first formed
     * @return its slot
     */
    public static Slot knifeSlot(int index) {
        int first = 0;
        for (int width : ROW_WIDTHS) {
            if (index < first + width) {
                double height = cubeTopAt(first, KNIVES) + CLEARANCE + KNIFE_HALF_HEIGHT;
                return new Slot((index - first - (width - 1) * HALF) * KNIFE_SPACING, height);
            }
            first += width;
        }
        return knifeSlot(KNIVES - 1);
    }

    /**
     * A javelin's slot: the three side by side, left to right, just clear of the full cube.
     *
     * @param index the javelin's index, 0 the first formed
     * @return its slot
     */
    public static Slot javelinSlot(int index) {
        return new Slot((index - (JAVELINS - 1) * HALF) * JAVELIN_SPACING,
                cubeTopAt(0, JAVELINS) + CLEARANCE + JAVELIN_HALF_HEIGHT);
    }

    /**
     * The slot the one formed at an index hovers at.
     *
     * @param index    its index, 0 the first formed
     * @param javelins whether the stack is Dart's javelins rather than Knives' knives
     * @return its slot
     */
    public static Slot slotOf(int index, boolean javelins) {
        return javelins ? javelinSlot(index) : knifeSlot(index);
    }

    /**
     * The cube's edge at a count held: full at none, a step smaller per one
     * held, and gone once the stack is full.
     *
     * @param held  the count held
     * @param stack the most the hold stacks
     * @return the edge, in blocks
     */
    public static double cubeEdgeAt(int held, int stack) {
        return stack <= 0 ? FULL_EDGE : FULL_EDGE * Math.clamp(stack - held, 0, stack) / stack;
    }

    /**
     * The cube's size at a count held against its full size: 1 at none, 0 once the stack is full.
     *
     * @param held  the count held
     * @param stack the most the hold stacks
     * @return the share of the full cube
     */
    public static float cubeShareAt(int held, int stack) {
        return (float) (cubeEdgeAt(held, stack) / FULL_EDGE);
    }

    /**
     * The cube's top at a count held, above its center.
     *
     * @param held  the count held
     * @param stack the most the hold stacks
     * @return the top's height, in blocks
     */
    public static double cubeTopAt(int held, int stack) {
        return cubeEdgeAt(held, stack) * HALF;
    }

    /**
     * Where one forming spawns: the cube's top as it forms.
     *
     * @param heldBefore the count held before it forms
     * @param stack      the most the hold stacks
     * @return the spawn point
     */
    public static Slot spawnSlot(int heldBefore, int stack) {
        return new Slot(0, cubeTopAt(heldBefore, stack));
    }

    /**
     * The cube's center in a view: the steady point by the glove, ahead of
     * the eye, to the glove's side and below the look.
     *
     * @param eye        the eye
     * @param xRot       the view's pitch in degrees
     * @param yRot       the view's yaw in degrees
     * @param gloveRight whether the glove is in the right hand
     * @return the center
     */
    public static Vec3 cubeCenterInView(Vec3 eye, float xRot, float yRot, boolean gloveRight) {
        Vec3 ahead = Vec3.directionFromRotation(xRot, yRot);
        return eye.add(ahead.scale(GloveRest.AHEAD))
                .add(rightOf(yRot).scale(gloveRight ? GloveRest.TO_THE_GLOVE_SIDE : -GloveRest.TO_THE_GLOVE_SIDE))
                .subtract(upOf(xRot, yRot).scale(GloveRest.BELOW));
    }

    /**
     * The world point a slot stands at over a cube in a view.
     *
     * @param cubeCenter the cube's center
     * @param xRot       the view's pitch in degrees
     * @param yRot       the view's yaw in degrees
     * @param slot       the slot
     * @return the point
     */
    public static Vec3 pointOf(Vec3 cubeCenter, float xRot, float yRot, Slot slot) {
        return cubeCenter.add(rightOf(yRot).scale(slot.right())).add(upOf(xRot, yRot).scale(slot.up()));
    }

    private static Vec3 rightOf(float yRot) {
        return Vec3.directionFromRotation(0f, yRot + QUARTER_TURN);
    }

    private static Vec3 upOf(float xRot, float yRot) {
        return Vec3.directionFromRotation(xRot - QUARTER_TURN, yRot);
    }
}
