package com.mercuriusxeno.goo.ability.crystal;

import com.mercuriusxeno.goo.ability.TravelForm;
import net.minecraft.world.phys.Vec3;

/**
 * Where a held stack hovers over its blob cube, shared by the server that
 * throws each one from its slot and the client that draws it. The cube rests
 * straight above where the hand rests and shrinks a step for each one held,
 * gone once the stack is full. Knives' ten knives hover in an upside-down
 * pyramid over the hand, rows of 4, 3, 2 and 1 from the top down, filled by
 * diagonals and the pile held centered over the cube at every count; each row
 * stands just clear of the cube's top at the count its first knife forms at,
 * so the cube has shrunk clear of each row by the time a knife takes it.
 * Dart's three javelins hover in a triangle, 1 and 2 over 3, spaced by the
 * dart's size and centered the same way. Each one spawns at the cube's top.
 * decision knives-hover-in-an-upside-down-bowling-pin-pyramid
 * decision knives-hover-above-the-blob-cube-over-the-hand
 * decision knives-blob-shrinks-per-knife-and-vanishes-on-the-last
 * decision knives-cube-shrinks-clear-of-each-next-layer
 * decision dart-javelins-hover-over-the-shrinking-blob-cube
 */
public final class StackHover {

    /** Blocks a knife stands above and below its slot's height. */
    public static final double KNIFE_HALF_HEIGHT = 0.012;
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
    /**
     * A hovering knife's length, point to butt, in blocks: small enough that
     * the whole pyramid stands in the view over the hand.
     */
    public static final double KNIFE_HOVER_LENGTH = 0.12;
    /** A hovering javelin's length, in blocks, small enough that the whole triangle stands in the view. */
    public static final double JAVELIN_HOVER_LENGTH = 0.25;
    /** How far apart two javelins stand against the width across their fins: a quarter of a width clear. */
    static final double JAVELIN_SPACING_PER_WIDTH = 1.25;
    /** An equilateral triangle's height against its side, which the javelins' rows stand apart by. */
    private static final double TRIANGLE_HEIGHT_PER_SIDE = Math.sqrt(3) / 2;
    /** The knives the pyramid holds. */
    public static final int KNIVES = 10;
    /** The javelins the row holds. */
    public static final int JAVELINS = 3;
    /**
     * How far ahead of the eye the cube rests, along the look: as far as the
     * first-person glove's palm rests at the default field of view, which the
     * cube stands straight above.
     */
    static final double REST_AHEAD = 0.7159375;
    /** How far out to the glove's side of the look the cube rests, as far as that palm. */
    static final double REST_TO_THE_GLOVE_SIDE = 0.56;
    /** How far below the look the cube rests: the client's rise above that palm, 0.371 below the look. */
    static final double REST_BELOW = 0.15;
    /** The knives in the pyramid's rows, the top row first. */
    private static final int[] ROW_WIDTHS = {4, 3, 2, 1};
    /** The row each knife fills by diagonals, the top row 0: knives 1 2 4 7, 3 5 8, 6 9 and 10. */
    private static final int[] KNIFE_ROW = {0, 0, 1, 0, 1, 2, 0, 1, 2, 3};
    /** Each knife's place in its row, left to right. */
    private static final int[] KNIFE_PLACE = {0, 1, 0, 2, 1, 0, 3, 2, 1, 0};
    /** The index of the first knife each row takes. */
    private static final int[] ROW_FIRST_KNIFE = {0, 2, 5, 9};
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
     * A knife's slot in the pyramid, filled by diagonals: knives 1 2 4 7 in
     * the top row, 3 5 8 under them, then 6 9 and 10 nearest the cube, each
     * the next place right in its row. The pile, the knives held now, stands
     * centered over the cube at every count, so a standing knife shifts
     * sideways as the pile widens. Each row stands just clear of the cube's
     * top at the count its first knife forms at, 0, 2, 5 and 9 held.
     * decision knives-hover-in-an-upside-down-bowling-pin-pyramid
     * decision knives-cube-shrinks-clear-of-each-next-layer
     *
     * @param index the knife's index, 0 the first formed
     * @param held  the knives held
     * @return its slot
     */
    public static Slot knifeSlot(int index, int held) {
        int knife = Math.clamp(index, 0, KNIVES - 1);
        int row = KNIFE_ROW[knife];
        double height = cubeTopAt(ROW_FIRST_KNIFE[row], KNIVES) + CLEARANCE + KNIFE_HALF_HEIGHT;
        return new Slot(pyramidRight(knife) - pileMiddle(Math.clamp(held, knife + 1, KNIVES)), height);
    }

    // A knife's place across the full pyramid, each row centered under the top row's middle.
    private static double pyramidRight(int knife) {
        int width = ROW_WIDTHS[KNIFE_ROW[knife]];
        return (KNIFE_PLACE[knife] - (width - 1) * HALF) * KNIFE_SPACING;
    }

    // The middle of the pile the first knives held span across the full pyramid.
    private static double pileMiddle(int held) {
        double least = Double.MAX_VALUE;
        double greatest = -Double.MAX_VALUE;
        for (int knife = 0; knife < held; knife++) {
            least = Math.min(least, pyramidRight(knife));
            greatest = Math.max(greatest, pyramidRight(knife));
        }
        return (least + greatest) * HALF;
    }

    /**
     * A javelin's slot, at the size Dart's javelins hover at: see
     * {@link #javelinSlot(int, int, double)}.
     *
     * @param index the javelin's index, 0 the first formed
     * @param held  the javelins held
     * @return its slot
     */
    public static Slot javelinSlot(int index, int held) {
        return javelinSlot(index, held, TravelForm.DART.widthAcrossFins() * javelinHoverShare());
    }

    /**
     * The size a hovering javelin draws at against a thrown one's: its hover
     * length over the dart's whole length.
     *
     * @return the share of the flight size
     */
    public static double javelinHoverShare() {
        return JAVELIN_HOVER_LENGTH / TravelForm.DART.frontLength();
    }

    /**
     * A javelin's slot in a triangle spaced by the javelin's size: javelins 1
     * and 2 side by side in the upper row, javelin 3 under them midway
     * between, every two a fixed share of the width across the fins apart, so
     * retuning the dart's size moves the spacing with it. The lower row
     * stands just clear of the cube's top as javelin 3 forms, at 2 held, the
     * upper row a triangle's height above it, and the pile held stands
     * centered over the cube at every count.
     * decision dart-javelins-hover-over-the-shrinking-blob-cube
     *
     * @param index the javelin's index, 0 the first formed
     * @param held  the javelins held
     * @param width the javelin's drawn width across its fins, in blocks
     * @return its slot
     */
    public static Slot javelinSlot(int index, int held, double width) {
        double apart = width * JAVELIN_SPACING_PER_WIDTH;
        double lower = cubeTopAt(JAVELINS - 1, JAVELINS) + CLEARANCE + javelinHalfHeight(width);
        int javelin = Math.clamp(index, 0, JAVELINS - 1);
        // the upper pair stand half the spacing either side of the middle, a lone first javelin on it
        double pileShift = Math.clamp(held, javelin + 1, JAVELINS) == 1 ? apart * HALF : 0;
        return javelin == JAVELINS - 1 ? new Slot(0, lower)
                : new Slot((javelin - HALF) * apart + pileShift, lower + apart * TRIANGLE_HEIGHT_PER_SIDE);
    }

    /**
     * How far a javelin stands above and below its slot's height: half its
     * width across the fins, which stand out on every side of the shaft.
     *
     * @param width the javelin's drawn width across its fins, in blocks
     * @return the half height, in blocks
     */
    public static double javelinHalfHeight(double width) {
        return width * HALF;
    }

    /**
     * The slot the one formed at an index hovers at.
     *
     * @param index    its index, 0 the first formed
     * @param held     the count held
     * @param javelins whether the stack is Dart's javelins rather than Knives' knives
     * @return its slot
     */
    public static Slot slotOf(int index, int held, boolean javelins) {
        return javelins ? javelinSlot(index, held) : knifeSlot(index, held);
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
     * The cube's center in a view: the rest a non-centered blob lifts to at
     * the default field of view, straight above where the hand rests, ahead
     * of the eye, to the glove's side and below the look.
     * palm-blob-lifts-as-the-spray-source
     *
     * @param eye        the eye
     * @param xRot       the view's pitch in degrees
     * @param yRot       the view's yaw in degrees
     * @param gloveRight whether the glove is in the right hand
     * @return the center
     */
    public static Vec3 cubeCenterInView(Vec3 eye, float xRot, float yRot, boolean gloveRight) {
        Vec3 ahead = Vec3.directionFromRotation(xRot, yRot);
        return eye.add(ahead.scale(REST_AHEAD))
                .add(rightOf(yRot).scale(gloveRight ? REST_TO_THE_GLOVE_SIDE : -REST_TO_THE_GLOVE_SIDE))
                .subtract(upOf(xRot, yRot).scale(REST_BELOW));
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
