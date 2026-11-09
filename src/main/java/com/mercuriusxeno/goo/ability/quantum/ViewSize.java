package com.mercuriusxeno.goo.ability.quantum;

import net.minecraft.world.phys.Vec3;

/**
 * How large a gate shows on screen, and whether a view through it is too
 * small to read. The transform through each pair is rigid, so a gate's size
 * from the virtual eye at any depth is its size on the actual screen; a
 * nested view whose gate shows only a few pixels is skipped, and a gate the
 * main frame sees directly is drawn whatever its size.
 * nested-views-skip-when-tiny-or-outside-the-parent-opening
 */
public final class ViewSize {

    /** The view is too small to read: a nested gate showing fewer pixels than this draws no view. */
    public static final double TOO_SMALL_PIXELS = 8.0;

    private ViewSize() {
    }

    /**
     * How many pixels a gate's long side spans on screen, seen from an eye.
     *
     * @param eye        the seeing eye
     * @param gate       the gate
     * @param fovDegrees the camera's vertical field of view, in degrees
     * @param height     the window's height, in pixels
     * @return the pixels its long side spans
     */
    public static double pixelsAcross(Vec3 eye, Gate gate, double fovDegrees, int height) {
        double halfSpan = Math.max(gate.halfSpan(gate.right()), gate.halfSpan(gate.up()));
        double distance = Math.max(eye.distanceTo(gate.center()), NEAREST);
        // A span at a distance fills its share of the half-height the field of view reaches there.
        return height * halfSpan / (distance * Math.tan(Math.toRadians(fovDegrees) * HALF));
    }

    private static final double HALF = 0.5;

    /** The nearest an eye reads a gate from, so one at its face reads very large rather than without bound. */
    private static final double NEAREST = 1.0E-3;

    /**
     * Whether a view at a depth is skipped for its gate's size on screen.
     *
     * @param depth  how many views deep it stands, one for a gate the main frame sees
     * @param pixels the pixels its gate spans on screen
     * @return true where it is too small to draw
     */
    public static boolean skips(int depth, double pixels) {
        return depth > 1 && pixels < TOO_SMALL_PIXELS;
    }
}
