package com.mercuriusxeno.goo.client.radial;

/**
 * The pure geometry of one radial wedge's mask: a petal whose outer end is
 * a rounded cap bridging its two radial edges rather than a cut of the
 * wheel's circle (decision wedges-round-off-like-petals).
 *
 * <p>The cap is the circle tangent to both radial edges whose farthest
 * point reaches the outer radius on the wedge's center angle. A wedge at
 * least a half turn wide has no tip to round, so it keeps the circle cut.
 * Coordinates are normalized to the wheel: center 0, rim 1, y down positive.
 */
final class PetalMask {

    private static final double TWO_PI = 2.0 * Math.PI;
    private static final double HALF = 0.5;

    private PetalMask() {
    }

    /**
     * Whether a point lies inside a wedge's petal.
     *
     * @param x          normalized x (-1..1, center = 0)
     * @param y          normalized y (-1..1, center = 0, down positive)
     * @param startAngle the wedge's start, clockwise from the top, in [0, 2 pi)
     * @param arc        the wedge's span in radians
     * @param innerNorm  the wedge's inner radius
     * @param outerNorm  the wedge's outer radius, reached at the tip of the cap
     * @return true if the point is inside the petal
     */
    static boolean isInsidePetal(double x, double y, double startAngle, double arc,
                                 double innerNorm, double outerNorm) {
        double distance = Math.hypot(x, y);
        if (distance < innerNorm || distance > outerNorm) {
            return false;
        }
        if (wrap(RadialWheel.angleOf(x, y) - startAngle) >= arc) {
            return false;
        }
        return arc >= Math.PI || isInsideCapOrStem(x, y, startAngle + arc * HALF, arc * HALF, outerNorm);
    }

    private static boolean isInsideCapOrStem(double x, double y, double centerAngle, double halfArc,
                                             double outerNorm) {
        double sinHalf = Math.sin(halfArc);
        double capCenter = outerNorm / (1.0 + sinHalf);
        double capRadius = capCenter * sinHalf;
        double axisX = Math.sin(centerAngle);
        double axisY = -Math.cos(centerAngle);
        double along = x * axisX + y * axisY;
        double cosHalf = Math.cos(halfArc);
        if (along <= capCenter * cosHalf * cosHalf) {
            return true;
        }
        return Math.hypot(x - axisX * capCenter, y - axisY * capCenter) <= capRadius;
    }

    private static double wrap(double angle) {
        double wrapped = angle % TWO_PI;
        return wrapped < 0 ? wrapped + TWO_PI : wrapped;
    }
}
