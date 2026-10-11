package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * The field an Unmake drink's surface is the level set of: every stream a
 * soft capsule chain along its skeleton with its liquid's radius, every
 * block still standing a soft rounded box, each body's field whole a little
 * inside its own surface and reaching {@link #REACH} outside it, that reach
 * shrinking in proportion for a body thinner than a waist so a body of no
 * radius radiates nothing, each skeleton read at its least distance and all
 * of them summed, so two bodies whose surfaces come within about a reach
 * swell into one another like metaballs touching, while a lone body's surface
 * sits exactly where its own does, an empty skeleton leaves no trace, and the
 * whole drink is one skin with no seam. The drink field shader marches this
 * same field on the GPU; this is its statement in Java, which DrinkUpload
 * packs and DrinkShaderTest holds the shader to.
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

        /**
         * @param point a point
         * @return the least stretched distance from the point to any body of the skeleton, {@link #REACH} at most
         */
        double leastDistanceTo(Vec3 point) {
            double least = REACH;
            for (int body = 0; body < bodies(); body++) {
                least = Math.min(least, distanceTo(body, point.x, point.y, point.z));
            }
            return least;
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
     * The field of a drink at a point, every skeleton read at its least distance and all summed.
     *
     * @param skeletons the drink's skeletons
     * @param point     the point
     * @return the field there
     */
    public static double valueAt(List<Skeleton> skeletons, Vec3 point) {
        double value = 0;
        for (Skeleton skeleton : skeletons) {
            value += falloff(skeleton.leastDistanceTo(point));
        }
        return value;
    }
}
