package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.Arrays;
import java.util.List;

/**
 * The field an Unmake drink's surface is the level set of: every stream a
 * soft capsule chain along its skeleton with its liquid's radius, every
 * block still standing a soft rounded box, each body's field whole a little
 * inside its own surface and reaching {@link #REACH} outside it, that reach
 * shrinking in proportion for a body thinner than a waist so a body of no
 * radius radiates nothing, and all of them summed, so two bodies whose
 * surfaces come within about a reach swell into one another like metaballs
 * touching, while a lone body's surface sits exactly where its own does, an
 * empty skeleton leaves no trace, and the whole drink is one skin with no seam.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public final class DrinkField {

    /** Blocks inside a body's own surface its field is still rising to whole. */
    public static final double DEPTH = 0.05;
    /** Blocks outside a body's own surface its field reaches, over which near bodies merge. */
    public static final double REACH = 0.24;
    /** The field's level the surface sits at: a lone body's field at its own surface. */
    public static final double ISO = falloff(0);
    /** The radius at and over which a body's field reaches its whole reach; a thinner body reaches less in proportion. */
    public static final double FULL_RADIUS = DrinkStream.WAIST;
    private static final double TWO = 2;
    private static final double THREE = 3;
    private static final double SIX = 6;
    /** How steeply a lone body's field falls off at its own surface, per block. */
    public static final double SLOPE = SIX * (DEPTH / (DEPTH + REACH)) * (REACH / (DEPTH + REACH)) / (DEPTH + REACH);
    private static final int NONE = -1;

    private DrinkField() {
    }

    /**
     * One stream's part of the field.
     *
     * @param stream the stream
     * @param rings  its skeleton, from its block's far side to its path's end
     * @param box    its block's box of liquid, or null once the block is gone
     */
    public record Skeleton(DrinkTree.Stream stream, List<DrinkStream.Ring> rings, DrinkBody.@Nullable Box box) {

        /**
         * @return how many bodies the skeleton has: a segment between each pair of rings, and its box
         */
        int bodies() {
            return Math.max(0, rings.size() - 1) + (box == null ? 0 : 1);
        }

        /**
         * @param body a body's index: a segment's, or the box's at the end
         * @return the lowest corner of the box about the body's own surface
         */
        Vec3 lowOf(int body) {
            if (body < rings.size() - 1) {
                DrinkStream.Ring from = rings.get(body);
                DrinkStream.Ring to = rings.get(body + 1);
                double radius = Math.max(from.radius(), to.radius());
                return new Vec3(Math.min(from.center().x, to.center().x) - radius,
                        Math.min(from.center().y, to.center().y) - radius,
                        Math.min(from.center().z, to.center().z) - radius);
            }
            return box == null ? Vec3.ZERO : box.center().subtract(box.half(), box.half(), box.half());
        }

        /**
         * @param body a body's index: a segment's, or the box's at the end
         * @return the highest corner of the box about the body's own surface
         */
        Vec3 highOf(int body) {
            if (body < rings.size() - 1) {
                DrinkStream.Ring from = rings.get(body);
                DrinkStream.Ring to = rings.get(body + 1);
                double radius = Math.max(from.radius(), to.radius());
                return new Vec3(Math.max(from.center().x, to.center().x) + radius,
                        Math.max(from.center().y, to.center().y) + radius,
                        Math.max(from.center().z, to.center().z) + radius);
            }
            return box == null ? Vec3.ZERO : box.center().add(box.half(), box.half(), box.half());
        }

        /**
         * @param body a body's index: a segment's, or the box's at the end
         * @param x    a point's x
         * @param y    its y
         * @param z    its z
         * @return the signed distance from the point to that body's surface, below zero inside, stretched for a
         *         body thinner than a waist so its field reaches less
         */
        double distanceTo(int body, double x, double y, double z) {
            if (body < rings.size() - 1) {
                return segmentDistance(rings.get(body), rings.get(body + 1), x, y, z);
            }
            return box == null ? REACH : scaled(box.signedDistance(new Vec3(x, y, z)), box.half());
        }
    }

    /**
     * The field at a point, with the skeleton nearest it.
     *
     * @param value    the field's value there
     * @param skeleton the skeleton nearest the point, or null where none reaches it
     * @param ring     the ring of that skeleton nearest the point
     */
    public record Sample(double value, @Nullable Skeleton skeleton, DrinkStream.@Nullable Ring ring) {

        /**
         * @return whether the point is inside the surface
         */
        public boolean inside() {
            return value >= ISO;
        }
    }

    /**
     * @param signed the signed distance to a body's own surface, below zero inside
     * @return the body's field there: 1 at {@link #DEPTH} inside and deeper, {@link #ISO} at its surface, 0 at
     *         {@link #REACH} outside and beyond
     */
    public static double falloff(double signed) {
        double t = Math.clamp((signed + DEPTH) / (DEPTH + REACH), 0, 1);
        return 1 - t * t * (THREE - TWO * t);
    }

    /**
     * @param skeleton a skeleton's index
     * @param body     a body's index within it
     * @return the two packed as one candidate for a cell
     */
    public static int candidate(int skeleton, int body) {
        return skeleton << Short.SIZE | body;
    }

    /**
     * @param signed the signed distance to a body's own surface
     * @param radius the body's radius there
     * @return the distance stretched by how much thinner than {@link #FULL_RADIUS} the body is, so its field
     *         reaches in proportion; a body of no radius reaches nothing
     */
    static double scaled(double signed, double radius) {
        double scale = Math.min(1, radius / FULL_RADIUS);
        return scale > 0 ? signed / scale : REACH;
    }

    /**
     * @param from the segment's first ring
     * @param to   its second
     * @param x    a point's x
     * @param y    its y
     * @param z    its z
     * @return the signed distance from the point to the capsule between the rings, their radii blended along it,
     *         stretched where the capsule is thinner than a waist
     */
    static double segmentDistance(DrinkStream.Ring from, DrinkStream.Ring to, double x, double y, double z) {
        double ax = to.center().x - from.center().x;
        double ay = to.center().y - from.center().y;
        double az = to.center().z - from.center().z;
        double px = x - from.center().x;
        double py = y - from.center().y;
        double pz = z - from.center().z;
        double length = ax * ax + ay * ay + az * az;
        double t = length == 0 ? 0 : Math.clamp((px * ax + py * ay + pz * az) / length, 0, 1);
        double dx = px - ax * t;
        double dy = py - ay * t;
        double dz = pz - az * t;
        double radius = from.radius() + (to.radius() - from.radius()) * t;
        return scaled(Math.sqrt(dx * dx + dy * dy + dz * dz) - radius, radius);
    }

    /**
     * The ring of a chain nearest a point.
     *
     * @param rings the chain
     * @param point the point
     * @return the nearest ring, or null for an empty chain
     */
    public static DrinkStream.@Nullable Ring nearestRing(List<DrinkStream.Ring> rings, Vec3 point) {
        DrinkStream.Ring nearest = null;
        double least = Double.MAX_VALUE;
        for (DrinkStream.Ring ring : rings) {
            double distance = ring.center().distanceToSqr(point);
            if (distance < least) {
                least = distance;
                nearest = ring;
            }
        }
        return nearest;
    }

    /**
     * The field of a drink at a point, every body of every skeleton read.
     *
     * @param skeletons the drink's skeletons
     * @param point     the point
     * @return the field there, with the skeleton nearest the point
     */
    public static Sample sample(List<Skeleton> skeletons, Vec3 point) {
        double[] least = new double[skeletons.size()];
        for (int index = 0; index < skeletons.size(); index++) {
            least[index] = REACH;
            for (int body = 0; body < skeletons.get(index).bodies(); body++) {
                least[index] = Math.min(least[index], skeletons.get(index).distanceTo(body, point.x, point.y,
                        point.z));
            }
        }
        return sampleOf(skeletons, least, point);
    }

    /**
     * The field of a drink at a point, only the bodies named read: those a
     * cell was marked by, which are every body that reaches any point of it.
     *
     * @param skeletons  the drink's skeletons
     * @param candidates the bodies to read, each packed by {@link #candidate}
     * @param point      the point
     * @return the field there, with the skeleton nearest the point
     */
    public static Sample sample(List<Skeleton> skeletons, int[] candidates, Vec3 point) {
        return sampleOf(skeletons, leastOf(skeletons, candidates, point.x, point.y, point.z), point);
    }

    /**
     * The field's value alone at a point, only the bodies named read.
     *
     * @param skeletons  the drink's skeletons
     * @param candidates the bodies to read, each packed by {@link #candidate}
     * @param x          the point's x
     * @param y          its y
     * @param z          its z
     * @return the field there
     */
    public static double valueAt(List<Skeleton> skeletons, int[] candidates, double x, double y, double z) {
        return valueAt(skeletons, candidates, x, y, z, new double[skeletons.size()]);
    }

    /**
     * The field's value alone at a point, only the bodies named read, with
     * no allocation: the caller lends the scratch the distances are gathered in.
     *
     * @param skeletons  the drink's skeletons
     * @param candidates the bodies to read, each packed by {@link #candidate}
     * @param x          the point's x
     * @param y          its y
     * @param z          its z
     * @param scratch    a scratch array as long as the skeletons, overwritten
     * @return the field there
     */
    public static double valueAt(List<Skeleton> skeletons, int[] candidates, double x, double y, double z,
                                 double[] scratch) {
        double value = 0;
        for (double least : leastOf(skeletons, candidates, x, y, z, scratch)) {
            value += falloff(least);
        }
        return value;
    }

    private static double[] leastOf(List<Skeleton> skeletons, int[] candidates, double x, double y, double z) {
        return leastOf(skeletons, candidates, x, y, z, new double[skeletons.size()]);
    }

    private static double[] leastOf(List<Skeleton> skeletons, int[] candidates, double x, double y, double z,
                                    double[] least) {
        Arrays.fill(least, REACH);
        for (int candidate : candidates) {
            int index = candidate >>> Short.SIZE;
            int body = candidate & Short.MAX_VALUE;
            least[index] = Math.min(least[index], skeletons.get(index).distanceTo(body, x, y, z));
        }
        return least;
    }

    private static Sample sampleOf(List<Skeleton> skeletons, double[] least, Vec3 point) {
        double value = 0;
        int nearest = NONE;
        for (int index = 0; index < least.length; index++) {
            value += falloff(least[index]);
            if (nearest == NONE || least[index] < least[nearest]) {
                nearest = index;
            }
        }
        if (nearest == NONE || least[nearest] >= REACH) {
            return new Sample(value, null, null);
        }
        Skeleton skeleton = skeletons.get(nearest);
        return new Sample(value, skeleton, nearestRing(skeleton.rings(), point));
    }
}
