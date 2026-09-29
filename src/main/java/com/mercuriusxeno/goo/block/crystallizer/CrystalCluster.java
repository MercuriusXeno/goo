package com.mercuriusxeno.goo.block.crystallizer;

import com.mercuriusxeno.goo.item.ChrysmTier;
import java.util.ArrayList;
import java.util.List;

/**
 * The quartz crystal growing from the crystallizer's purple spot, as plain
 * geometry (decision crystallizer-emits-chrysm). Operator ruling: something
 * quartz shaped, angled prisms jutting out of a base at odd 22.5 incremental
 * angles, growing steadily until it can't anymore, visibly the next tier; hacked
 * procedurally until a drawn crystal replaces it. Growth reads the crystallizer's
 * pace: a quarter per tier, even with the goo inside the tier. Operator ruling:
 * through the materia tier the crystal compresses, its buds retracting while the
 * central prism shrinks and its six sides round into 60 degree spheroid segments,
 * until a marble stands alone. The crystallizer stops crystallizing at the knob's
 * tier, so the cluster stops there too.
 */
public final class CrystalCluster {

    /** The purple spot on the top face, in model pixels with the dial on the south face. */
    public static final double BASE_X = 8;
    public static final double BASE_Z = 11;
    /** The top face the cluster stands on, in model pixels. */
    public static final double BASE_Y = 16;

    /** Every angle the cluster uses is a multiple of this. */
    public static final double ANGLE_STEP = 22.5;

    /** Each tier is an even share of full growth. */
    private static final double TIER_SHARE = 1.0 / ChrysmTier.values().length;
    /** The cluster grows over every tier below materia; materia's share compresses it. */
    private static final double CLUSTER_SHARE = 1 - TIER_SHARE;
    /** The materia marble's radius, in model pixels, smaller than the prism it rounds from. */
    public static final double ORB_RADIUS = 2;
    private static final double ORB_DIAMETER = 2 * ORB_RADIUS;
    /** Each client tick closes this share of the gap between the drawn growth and the synced growth. */
    private static final double EASE_SHARE = 0.3;
    private static final double EASE_SNAP = 1e-4;
    private static final double MAX_LENGTH = 12;
    private static final double MAX_RADIUS = 2.5;
    private static final double TIP_FRACTION = 0.25;
    /** A new prism starts at this share of its full radius, so it never draws as a needle. */
    private static final double RADIUS_FLOOR = 0.35;

    /**
     * One prism of the cluster.
     *
     * @param tilt     degrees from vertical, a multiple of 22.5
     * @param yaw      degrees around the vertical, a multiple of 22.5
     * @param length   the prism's length to its tip, in pixels
     * @param radius   the prism's hexagon radius, in pixels
     * @param rounding how far its faces have rounded, 0 for the hexagonal prism, 1 for
     *                 the spheroid its length and radius span
     */
    public record Prism(double tilt, double yaw, double length, double radius, double rounding) {

        /**
         * A prism with flat faces.
         *
         * @param tilt   degrees from vertical, a multiple of 22.5
         * @param yaw    degrees around the vertical, a multiple of 22.5
         * @param length the prism's length to its tip, in pixels
         * @param radius the prism's hexagon radius, in pixels
         */
        public Prism(double tilt, double yaw, double length, double radius) {
            this(tilt, yaw, length, radius, 0);
        }

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
     * How far the crystal has grown: a quarter per tier, even with the goo inside the
     * tier as the crystallizer's pace is (operator ruling).
     *
     * @param crystallized the crystallized volume, in mB
     * @return the growth, from 0 for nothing to 1 for a materia
     */
    public static double growth(long crystallized) {
        if (crystallized <= 0) {
            return 0;
        }
        if (crystallized >= ChrysmTier.MATERIA.volume()) {
            return 1;
        }
        ChrysmTier tier = CrystallizerPhases.growingTier(crystallized);
        long start = CrystallizerPhases.spanStart(tier);
        double withinTier = (double) (crystallized - start) / (tier.volume() - start);
        return (tier.ordinal() + withinTier) * TIER_SHARE;
    }

    /**
     * @param growth how far the crystal has grown, from 0 to 1
     * @return how far it has compressed toward the marble, 0 until flowering chrysm, 1 at materia
     */
    public static double compression(double growth) {
        return Math.max(0, Math.min(1, (growth - CLUSTER_SHARE) / TIER_SHARE));
    }

    /**
     * Eases the drawn growth toward the synced growth, one client tick: it closes a
     * share of the gap going up, and snaps straight down when a chrysm is taken.
     *
     * @param displayed the growth drawn last tick
     * @param target    the growth the synced volume reads
     * @return the growth to draw this tick
     */
    public static double ease(double displayed, double target) {
        if (target <= displayed || target - displayed < EASE_SNAP) {
            return target;
        }
        return displayed + (target - displayed) * EASE_SHARE;
    }

    /**
     * @param crystallized the crystallized volume, in mB
     * @return the prisms grown so far, the central one first; none for nothing crystallized
     */
    public static List<Prism> prisms(long crystallized) {
        return prisms(growth(crystallized));
    }

    /**
     * @param growth how far the cluster has grown, from 0 to 1
     * @return the prisms grown so far, the central one first; none for no growth
     */
    public static List<Prism> prisms(double growth) {
        double clusterGrowth = Math.min(1, growth / CLUSTER_SHARE);
        double compressed = compression(growth);
        List<Prism> prisms = new ArrayList<>();
        for (Seed seed : SEEDS) {
            if (clusterGrowth <= seed.birth()) {
                continue;
            }
            double grown = (clusterGrowth - seed.birth()) / (1 - seed.birth());
            Prism prism = new Prism(seed.tilt(), seed.yaw(), MAX_LENGTH * seed.lengthShare() * grown,
                    MAX_RADIUS * seed.radiusShare() * (RADIUS_FLOOR + (1 - RADIUS_FLOOR) * grown));
            if (prisms.isEmpty()) {
                prisms.add(compress(prism, compressed));
            } else if (compressed < 1) {
                prisms.add(retract(prism, compressed));
            }
        }
        return prisms;
    }

    /**
     * The central prism compressing into the marble: its length and radius shrink to
     * the marble's as its faces round.
     *
     * @param prism      the full-grown central prism
     * @param compressed how far it has compressed, from 0 to 1
     * @return the prism at that compression
     */
    private static Prism compress(Prism prism, double compressed) {
        return new Prism(prism.tilt(), prism.yaw(), lerp(prism.length(), ORB_DIAMETER, compressed),
                lerp(prism.radius(), ORB_RADIUS, compressed), compressed);
    }

    /**
     * A bud retracting into the base as the crystal compresses.
     *
     * @param prism      the full-grown bud
     * @param compressed how far the crystal has compressed, from 0 to 1
     * @return the bud at that compression
     */
    private static Prism retract(Prism prism, double compressed) {
        double kept = 1 - compressed;
        return new Prism(prism.tilt(), prism.yaw(), prism.length() * kept, prism.radius() * kept);
    }

    private static double lerp(double from, double to, double share) {
        return from + (to - from) * share;
    }

    /**
     * The box a click on the cluster lands in: its reach around the spot and its height.
     *
     * @param crystallized the crystallized volume, in mB
     * @return {half width, height} in pixels, zero for nothing crystallized
     */
    public static double[] reach(long crystallized) {
        return reach(growth(crystallized));
    }

    /**
     * @param growth how far the cluster has grown, from 0 to 1
     * @return {half width, height} in pixels, zero for no growth
     */
    public static double[] reach(double growth) {
        double halfWidth = 0;
        double height = 0;
        for (Prism prism : prisms(growth)) {
            double tilt = Math.toRadians(prism.tilt());
            halfWidth = Math.max(halfWidth, prism.length() * Math.sin(tilt) + prism.radius());
            height = Math.max(height, prism.length() * Math.cos(tilt));
        }
        return new double[] {halfWidth, height};
    }
}
