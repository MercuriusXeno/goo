package com.mercuriusxeno.goo.block.crystallizer;

import java.util.ArrayList;
import java.util.List;

/**
 * The quartz crystal growing from the crystallizer's purple spot, as plain
 * geometry (decision crystallizer-emits-chrysm). Operator ruling: something
 * quartz shaped, angled prisms jutting out of a base at odd 22.5 incremental
 * angles, growing steadily until it can't anymore, visibly the next tier; hacked
 * procedurally until a drawn crystal replaces it. Growth reads the crystallized
 * volume on a log scale, so each tier, 1,000 times the last, stands a third
 * taller; the crystallizer stops crystallizing at the knob's tier, so the cluster
 * stops there too.
 */
public final class CrystalCluster {

    /** The purple spot on the top face, in model pixels with the dial on the south face. */
    public static final double BASE_X = 8;
    public static final double BASE_Z = 11;
    /** The top face the cluster stands on, in model pixels. */
    public static final double BASE_Y = 16;

    /** Every angle the cluster uses is a multiple of this. */
    public static final double ANGLE_STEP = 22.5;

    /** A megachrysm's crystallized volume, 10^9 mB, is full growth. */
    private static final double FULL_GROWTH_LOG = 9;
    private static final double MAX_LENGTH = 12;
    private static final double MAX_RADIUS = 2.5;
    private static final double TIP_FRACTION = 0.25;
    /** A new prism starts at this share of its full radius, so it never draws as a needle. */
    private static final double RADIUS_FLOOR = 0.35;

    /**
     * One prism of the cluster.
     *
     * @param tilt   degrees from vertical, a multiple of 22.5
     * @param yaw    degrees around the vertical, a multiple of 22.5
     * @param length the prism's length to its tip, in pixels
     * @param radius the prism's hexagon radius, in pixels
     */
    public record Prism(double tilt, double yaw, double length, double radius) {

        /**
         * @return the share of the length the pointed tip takes
         */
        public double tipLength() {
            return length * TIP_FRACTION;
        }
    }

    /**
     * One prism of the template: its angles, the growth it appears at and its share of full size.
     */
    private record Seed(double tilt, double yaw, double birth, double lengthShare, double radiusShare) {
    }

    /** The cluster's prisms, the central one first; each appears once growth passes its birth. */
    private static final List<Seed> SEEDS = List.of(
            new Seed(0, 0, 0, 1.0, 1.0),
            new Seed(ANGLE_STEP, 2 * ANGLE_STEP, 0.15, 0.7, 0.8),
            new Seed(ANGLE_STEP, 9 * ANGLE_STEP, 0.25, 0.65, 0.75),
            new Seed(2 * ANGLE_STEP, 5 * ANGLE_STEP, 0.4, 0.55, 0.7),
            new Seed(2 * ANGLE_STEP, 13 * ANGLE_STEP, 0.5, 0.5, 0.65),
            new Seed(ANGLE_STEP, 15 * ANGLE_STEP, 0.6, 0.6, 0.7),
            new Seed(2 * ANGLE_STEP, ANGLE_STEP, 0.75, 0.45, 0.6));

    private CrystalCluster() {
    }

    /**
     * How far the cluster has grown: the crystallized volume's order of magnitude
     * over a megachrysm's.
     *
     * @param crystallized the crystallized volume, in mB
     * @return the growth, from 0 for nothing to 1 for a megachrysm
     */
    public static double growth(long crystallized) {
        if (crystallized <= 0) {
            return 0;
        }
        return Math.min(1, Math.log10(Math.max(1, crystallized)) / FULL_GROWTH_LOG);
    }

    /**
     * @param crystallized the crystallized volume, in mB
     * @return the prisms grown so far, the central one first; none for nothing crystallized
     */
    public static List<Prism> prisms(long crystallized) {
        double growth = growth(crystallized);
        List<Prism> prisms = new ArrayList<>();
        for (Seed seed : SEEDS) {
            if (growth <= seed.birth()) {
                continue;
            }
            double grown = (growth - seed.birth()) / (1 - seed.birth());
            prisms.add(new Prism(seed.tilt(), seed.yaw(), MAX_LENGTH * seed.lengthShare() * grown,
                    MAX_RADIUS * seed.radiusShare() * (RADIUS_FLOOR + (1 - RADIUS_FLOOR) * grown)));
        }
        return prisms;
    }

    /**
     * The box a click on the cluster lands in: its reach around the spot and its height.
     *
     * @param crystallized the crystallized volume, in mB
     * @return {half width, height} in pixels, zero for nothing crystallized
     */
    public static double[] reach(long crystallized) {
        double halfWidth = 0;
        double height = 0;
        for (Prism prism : prisms(crystallized)) {
            double tilt = Math.toRadians(prism.tilt());
            halfWidth = Math.max(halfWidth, prism.length() * Math.sin(tilt) + prism.radius());
            height = Math.max(height, prism.length() * Math.cos(tilt));
        }
        return new double[] {halfWidth, height};
    }
}
