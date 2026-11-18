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
    public static final double KNIFE_HALF_HEIGHT = 0.022 * 2 / 3;
    /**
     * The full cube's edge, in blocks: the lifted palm blob's greatest drawn edge,
     * twice the flight shell's 0.15 half width at the palm rest's half flight size
     * and the lifted beat's greatest 0.9, so the stack stands over the cube drawn.
     */
    static final double FULL_EDGE = 2 * 0.15 * 0.5 * 0.9;
    /** Blocks between the cube's top and the lowest point of the row standing over it. */
    static final double CLEARANCE = 0.03;
    /** How far a knife row stands over the row under it: a blade's thickness and the clearance. */
    private static final double ROW_GAP = KNIFE_HALF_HEIGHT * 2 + CLEARANCE;
    /** A knife's whole length, point to butt, in blocks, hovering or thrown alike. */
    public static final double KNIFE_LENGTH = 0.4;
    /** A knife's blade width at its widest, in blocks, which the knives' spacing follows. */
    public static final double KNIFE_WIDTH = 0.1;
    /** How far apart two javelins stand against their width: a quarter of a width clear. */
    static final double SPACING_PER_WIDTH = 1.25;
    /** How far apart two knives stand against a blade's width: a whole blade's width clear. */
    static final double KNIFE_SPACING_PER_WIDTH = 2;
    /** Blocks between neighbouring knives along a row. */
    static final double KNIFE_SPACING = KNIFE_WIDTH * KNIFE_SPACING_PER_WIDTH;
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
    /** How far below the look the first-person glove's palm rests at the default field of view. */
    static final double PALM_BELOW_THE_LOOK = 0.37104167;
    /**
     * How far up the view a stack's cube, Knives' or Dart's, stands above the
     * drawn palm: one full cube edge, the cube clear of the palm it lifted from.
     */
    public static final double KNIFE_RISE_ABOVE_THE_PALM = FULL_EDGE;
    /** How far below the look a stack's cube rests: that rise above the palm. */
    static final double STACK_REST_BELOW = PALM_BELOW_THE_LOOK - KNIFE_RISE_ABOVE_THE_PALM;
    /** The knives in the pyramid's rows, the top row first. */
    private static final int[] ROW_WIDTHS = {4, 3, 2, 1};
    /** The row each knife fills by diagonals, the top row 0: knives 1 2 4 7, 3 5 8, 6 9 and 10. */
    private static final int[] KNIFE_ROW = {0, 0, 1, 0, 1, 2, 0, 1, 2, 3};
    /** Each knife's place in its row, left to right. */
    private static final int[] KNIFE_PLACE = {0, 1, 0, 2, 1, 0, 3, 2, 1, 0};
    /** The index of the first knife each row takes. */
    private static final int[] ROW_FIRST_KNIFE = {0, 2, 5, 9};
    private static final double HALF = 0.5;
    /** The share of the buffer a full stack takes: all but its 10% floor. */
    private static final double STACK_SHARE = 0.9;
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
        return new Slot(pyramidRight(knife) - pileMiddle(Math.clamp(held, knife + 1, KNIVES)),
                rowHeight(KNIFE_ROW[knife]));
    }

    // A row's height over the cube: clear of the cube's top at the count its first knife forms at, and a blade's
    // thickness and the clearance over the row under it, so no row passes through the next.
    // knives-hover-in-an-upside-down-bowling-pin-pyramid
    private static double rowHeight(int row) {
        double overTheCube = cubeTopAt(ROW_FIRST_KNIFE[row], KNIVES) + CLEARANCE + KNIFE_HALF_HEIGHT;
        return row == ROW_WIDTHS.length - 1 ? overTheCube
                : Math.max(overTheCube, rowHeight(row + 1) + ROW_GAP);
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
        return javelinSlot(index, held, TravelForm.DART.widthAcrossFins());
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
        double apart = width * SPACING_PER_WIDTH;
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
     * The cube's edge at a fill of the buffer, by volume: the cube holds the
     * fill's share of the full cube's volume, so its edge is the full edge
     * times the fill's cube root, 0.464 of it at the 10% floor.
     * decision palm-blob-and-cutaway-cuboid-size-by-the-buffers-volume
     *
     * @param fill the fill, 0 to 1
     * @return the edge, in blocks
     */
    public static double edgeAtFill(double fill) {
        return FULL_EDGE * Math.cbrt(Math.clamp(fill, 0.0, 1.0));
    }

    /**
     * The edge of the cuboid a forming weapon is cut from the palm blob as:
     * the weapon's share of the full blob's volume, the stack's 90% over its
     * count, so 0.448 of the full edge for a knife and 0.669 for a javelin.
     * decision palm-blob-and-cutaway-cuboid-size-by-the-buffers-volume
     *
     * @param stack the most the hold stacks
     * @return the edge, in blocks
     */
    public static double cuboidEdgeOf(int stack) {
        return stack <= 0 ? 0 : edgeAtFill(shareOf(stack));
    }

    /**
     * One weapon's share of the full blob's volume: the stack's 90% over its count.
     *
     * @param stack the most the hold stacks
     * @return the share, 0 to 1
     */
    public static double shareOf(int stack) {
        return stack <= 0 ? 0 : STACK_SHARE / stack;
    }

    /**
     * The cube's edge at a count held, at the largest fill that count allows:
     * 100% less the held shares, the stack's 90% over its count each, so the
     * cube stands at the 10% floor with the stack full and never vanishes.
     * decision held-weapons-reserve-their-share-of-the-buffer
     *
     * @param held  the count held
     * @param stack the most the hold stacks
     * @return the edge, in blocks
     */
    public static double cubeEdgeAt(int held, int stack) {
        if (stack <= 0) {
            return FULL_EDGE;
        }
        double sharesHeld = STACK_SHARE * Math.clamp(held, 0, stack) / stack;
        return edgeAtFill(1 - sharesHeld);
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
                .subtract(upOf(xRot, yRot).scale(STACK_REST_BELOW));
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
