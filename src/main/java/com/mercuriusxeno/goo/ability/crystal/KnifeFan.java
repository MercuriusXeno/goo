package com.mercuriusxeno.goo.ability.crystal;

import net.minecraft.world.phys.Vec3;

/**
 * Where Knives' formed knives hover: on one arc ahead of the eye and below
 * the crosshair, so they never block the aim, each knife's slot set by its
 * index and the count formed alone. The slots sit a fixed angle apart around
 * the look, centered on it, so the arc widens as knives form until it
 * reaches its widest, and past that the slots pack closer. The operator
 * chose this placement as the fan's algorithm.
 * decision knives-emerge-from-the-blob-and-hover-in-a-fan
 */
public final class KnifeFan {

    /** Blocks from the eye to the arc the knives hover on. */
    static final double RADIUS = 1.2;
    /** Blocks below the eye the arc stands. */
    static final double BELOW_THE_EYE = 0.35;
    /** Degrees between neighbouring knives while the arc is narrower than its widest. */
    static final double SPACING_DEGREES = 10;
    /** Degrees the arc spans, edge knife to edge knife, at its widest. */
    static final double WIDEST_DEGREES = 60;
    private static final double HALF = 0.5;

    private KnifeFan() {
    }

    /**
     * A knife's slot in the fan, in the view's own frame.
     *
     * @param right blocks to the right of the eye, across the view
     * @param up    blocks above the eye, up the view
     * @param ahead blocks ahead of the eye, along the look
     */
    public record Slot(double right, double up, double ahead) {
    }

    /**
     * A knife's slot: the leftmost knife first, each the spacing to the right
     * of the last, the whole arc centered on the look.
     *
     * @param index the knife's index, 0 the leftmost
     * @param count the knives formed
     * @return the slot
     */
    public static Slot slotOf(int index, int count) {
        double spacing = count <= 1 ? 0 : Math.min(SPACING_DEGREES, WIDEST_DEGREES / (count - 1));
        double angle = Math.toRadians((index - (count - 1) * HALF) * spacing);
        return new Slot(RADIUS * Math.sin(angle), -BELOW_THE_EYE, RADIUS * Math.cos(angle));
    }

    /**
     * The world point a slot stands at for an eye and a look: the slot's
     * offsets taken along the view's right, up and ahead.
     *
     * @param eye  the eye
     * @param look the look, a unit vector
     * @param slot the slot
     * @return the point
     */
    public static Vec3 pointOf(Vec3 eye, Vec3 look, Slot slot) {
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() > 0 ? right.normalize() : new Vec3(1, 0, 0);
        Vec3 up = right.cross(look).normalize();
        return eye.add(right.scale(slot.right())).add(up.scale(slot.up())).add(look.scale(slot.ahead()));
    }
}
