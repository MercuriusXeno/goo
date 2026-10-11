package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * Where an expanding sphere's front meets the world's surfaces: on each
 * block face open to air, the arc of the circle the sphere cuts in the
 * face's plane, kept to the face, so the front draws as a smooth line over
 * floors and walls rather than as lit blocks.
 * decision glitter-sphere-icons-gem-ore-groups
 */
public final class SurfaceArcs {

    /** Points an arc takes across one face. */
    static final int SAMPLES = 10;
    /** Points a whole circle takes where it lies inside one face. */
    private static final int WHOLE_CIRCLE_SAMPLES = 24;
    /** How far past a face's edge a point still counts as on it, against rounding. */
    private static final double EDGE = 1e-6;
    private static final double HALF = 0.5;
    /** The fewest points that make an arc's line. */
    private static final int FEWEST_POINTS = 2;
    /** A face square's corners. */
    private static final int CORNERS = 4;

    private SurfaceArcs() {
    }

    /**
     * The farthest a face's points lie from a center, past which the
     * growing front has left the face behind.
     *
     * @param center the sphere's center
     * @param pos    the block
     * @param side   the face's side
     * @return the distance to the face's farthest corner
     */
    static double farthest(Vec3 center, BlockPos pos, Direction side) {
        Vec3 faceCenter = Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(side.getUnitVec3i()).scale(HALF));
        return faceCenter.distanceTo(center) + Math.sqrt(HALF);
    }

    /**
     * The runs of points where a sphere cuts a block face, each run an arc
     * lying on the face and on the sphere.
     *
     * @param center the sphere's center
     * @param radius its radius
     * @param pos    the block
     * @param side   the face's side
     * @return the arcs, empty where the sphere misses the face
     */
    static List<List<Vec3>> arcs(Vec3 center, double radius, BlockPos pos, Direction side) {
        Direction.Axis axis = side.getAxis();
        Direction.Axis first = axis == Direction.Axis.X ? Direction.Axis.Y : Direction.Axis.X;
        Direction.Axis second = axis == Direction.Axis.Z ? Direction.Axis.Y : Direction.Axis.Z;
        double plane = pos.get(axis) + (side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0);
        double offPlane = plane - center.get(axis);
        if (Math.abs(offPlane) >= radius) {
            return List.of();
        }
        double ring = Math.sqrt(radius * radius - offPlane * offPlane);
        Square square = new Square(pos.get(first), pos.get(second), center.get(first), center.get(second));
        List<List<Vec3>> runs = new ArrayList<>();
        for (List<double[]> run : sample(square, ring)) {
            runs.add(run.stream().map(uv -> point(axis, first, second, plane, uv[0], uv[1])).toList());
        }
        return runs;
    }

    /**
     * Samples the circle across the window the square spans, splitting the
     * points into runs that lie on the square.
     *
     * @param square the face square, with the circle's center
     * @param ring   the circle's radius in the face's plane
     * @return the runs of in-plane points, each long enough to draw
     */
    private static List<List<double[]>> sample(Square square, double ring) {
        double[] window = square.window();
        int samples = window[1] - window[0] >= Math.TAU ? WHOLE_CIRCLE_SAMPLES : SAMPLES;
        List<List<double[]>> runs = new ArrayList<>();
        List<double[]> run = new ArrayList<>();
        for (int step = 0; step <= samples; step++) {
            double angle = window[0] + (window[1] - window[0]) * step / samples;
            double u = square.centerU() + ring * Math.cos(angle);
            double v = square.centerV() + ring * Math.sin(angle);
            if (square.holds(u, v)) {
                run.add(new double[]{u, v});
            } else {
                run = closeRun(runs, run);
            }
        }
        closeRun(runs, run);
        return runs;
    }

    private static List<double[]> closeRun(List<List<double[]>> runs, List<double[]> run) {
        if (run.size() >= FEWEST_POINTS) {
            runs.add(run);
        }
        return run.isEmpty() ? run : new ArrayList<>();
    }

    private static Vec3 point(Direction.Axis axis, Direction.Axis first, Direction.Axis second, double plane,
                              double u, double v) {
        double[] xyz = new double[Direction.Axis.values().length];
        xyz[axis.ordinal()] = plane;
        xyz[first.ordinal()] = u;
        xyz[second.ordinal()] = v;
        return new Vec3(xyz[Direction.Axis.X.ordinal()], xyz[Direction.Axis.Y.ordinal()],
                xyz[Direction.Axis.Z.ordinal()]);
    }

    /**
     * A unit face square in its plane's two axes, and the circle's center in them.
     *
     * @param lowU    the square's low corner along the first axis
     * @param lowV    the square's low corner along the second axis
     * @param centerU the circle's center along the first axis
     * @param centerV the circle's center along the second axis
     */
    private record Square(double lowU, double lowV, double centerU, double centerV) {

        boolean holds(double u, double v) {
            return u >= lowU - EDGE && u <= lowU + 1 + EDGE && v >= lowV - EDGE && v <= lowV + 1 + EDGE;
        }

        /**
         * The angles about the circle's center the square spans: the whole
         * turn where the center lies inside it, else the narrowest window
         * holding its four corners.
         *
         * @return the window's start and end angles, end past start
         */
        double[] window() {
            if (holds(centerU, centerV)) {
                return new double[]{0, Math.TAU};
            }
            double base = Math.atan2(lowV + HALF - centerV, lowU + HALF - centerU);
            double low = Double.MAX_VALUE;
            double high = -Double.MAX_VALUE;
            for (int corner = 0; corner < CORNERS; corner++) {
                double angle = Math.atan2(lowV + (corner >> 1) - centerV, lowU + (corner & 1) - centerU) - base;
                angle = Math.atan2(Math.sin(angle), Math.cos(angle));
                low = Math.min(low, angle);
                high = Math.max(high, angle);
            }
            return new double[]{base + low, base + high};
        }
    }
}
