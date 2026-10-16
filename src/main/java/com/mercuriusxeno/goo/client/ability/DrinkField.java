package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * The field an Unmake drink's surface is the level set of: every stream a
 * smooth tube along its skeleton with its liquid's radius, every
 * block still standing a rounded box, each body's field whole a little
 * inside its own surface and reaching {@link #REACH} outside it, that reach
 * shrinking in proportion for a body thinner than a waist so a body of no
 * radius radiates nothing. A stream is one smooth tube along a curve through
 * its rings, each segment owning the points between the planes square to the
 * stream's run at its two rings, so where the snake bends no ring creases the
 * skin, and the block's box is filleted onto it so the mouth shows no crease
 * either. Streams join by the two
 * nearest blended over a fillet as wide as their surfaces face apart, so two
 * streams meeting across a gap merge like metaballs while a tributary riding
 * its trunk's spine adds nothing to the trunk's width; a lone body's surface
 * sits exactly where its own does, an empty skeleton leaves no trace, and the
 * whole drink is one skin with no seam. Each stream's skin is a cyclone,
 * grooves twisting about its own axis and turning as the goo travels. The
 * drink field shader marches this
 * same field on the GPU; this is its statement in Java, which DrinkUpload
 * packs and DrinkShaderTest holds the shader to.
 * decision unmake-waves-dissolve-by-crucible-cost
 * decision diagnose-then-fix-unmake-seam-and-ribbing
 */
public final class DrinkField {

    /** Blocks inside a body's own surface its field is still rising to whole. */
    public static final double DEPTH = 0.05;
    /** Blocks outside a body's own surface its field reaches. */
    public static final double REACH = 0.24;
    /** The field's level the surface sits at: a lone body's field at its own surface. */
    public static final double ISO = falloff(0);
    /** The radius at and over which a body's field reaches its whole reach; a thinner body reaches less in proportion. */
    public static final double FULL_RADIUS = DrinkStream.WAIST;
    /**
     * Blocks over which streams union smoothly: a stream nearer than this to another merges into it as metaballs
     * do, and two lying wholly on one another swell by this times the log of two.
     */
    public static final double SOFTNESS = 0.08;
    private static final double TWO = 2;
    private static final double THREE = 3;
    private static final double FOUR = 4;
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private DrinkField() {
    }

    /**
     * What one body or one stream reads at a point.
     *
     * @param distance the stretched signed distance to its surface, below zero inside
     * @param facing   the unit direction its surface faces there, away from its middle
     */
    record Reading(double distance, Vec3 facing) {
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
         * @return how many segments the skeleton has
         */
        int segments() {
            return Math.max(0, rings.size() - 1);
        }

        /**
         * @param body a body's index: a segment's, or the box's at the end
         * @return the lowest corner of the box about the body's own surface
         */
        Vec3 lowOf(int body) {
            if (body < segments()) {
                DrinkStream.Ring from = rings.get(body);
                DrinkStream.Ring to = rings.get(body + 1);
                double radius = swellOf(body);
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
            if (body < segments()) {
                DrinkStream.Ring from = rings.get(body);
                DrinkStream.Ring to = rings.get(body + 1);
                double radius = swellOf(body);
                return new Vec3(Math.max(from.center().x, to.center().x) + radius,
                        Math.max(from.center().y, to.center().y) + radius,
                        Math.max(from.center().z, to.center().z) + radius);
            }
            return box == null ? Vec3.ZERO : box.center().add(box.half(), box.half(), box.half());
        }

        /**
         * @param segment a segment
         * @return how far the segment's surface stands off its straight line at most: its wider ring's radius
         */
        double swellOf(int segment) {
            return Math.max(rings.get(segment).radius(), rings.get(segment + 1).radius());
        }

        /**
         * The stream's reading at a point: the nearest of the spheres swept
         * from ring to ring along its path, the round cone hull of each pair,
         * so two segments meeting at a ring share that ring's sphere exactly,
         * and its block's body unioned onto them by the soft minimum. Curved
         * segments owning the points between planes, a fallback cone where none
         * did, a box filleted on and grooves carved in each met the others its
         * own way, and every such meeting showed as a seam.
         *
         * @param point a point
         * @return the reading, {@link #REACH} away where the stream has no body
         */
        Reading readingAt(Vec3 point) {
            Reading nearest = new Reading(REACH, UP);
            for (int segment = 0; segment < segments(); segment++) {
                Reading reading = segmentReading(rings.get(segment), rings.get(segment + 1), point);
                if (reading.distance() < nearest.distance()) {
                    nearest = reading;
                }
            }
            if (box == null) {
                return nearest;
            }
            Reading boxed = boxReading(box, point);
            return new Reading(unionOf(weightOf(nearest.distance()) + weightOf(boxed.distance())),
                    boxed.distance() < nearest.distance() ? boxed.facing() : nearest.facing());
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
     * @param from  the segment's first ring
     * @param to    its second
     * @param point a point
     * @return the point's reading off the round cone between the rings, stretched where the cone is thinner than a
     *         waist, facing away from the point's foot on the segment's line
     */
    static Reading segmentReading(DrinkStream.Ring from, DrinkStream.Ring to, Vec3 point) {
        Vec3 line = to.center().subtract(from.center());
        Vec3 rel = point.subtract(from.center());
        double length = line.lengthSqr();
        double t = length == 0 ? 0 : Math.clamp(rel.dot(line) / length, 0, 1);
        double radius = from.radius() + (to.radius() - from.radius()) * t;
        Vec3 off = rel.subtract(line.scale(t));
        Vec3 facing = off.lengthSqr() > 0 ? off.normalize() : UP;
        return new Reading(scaled(roundCone(rel.x, rel.y, rel.z, line.x, line.y, line.z, from.radius(),
                to.radius()), radius), facing);
    }

    /**
     * @param box   a block's box of liquid
     * @param point a point
     * @return the point's reading off the box, facing out of the face or the rounded edge nearest it
     */
    static Reading boxReading(DrinkBody.Box box, Vec3 point) {
        double core = box.half() - box.rounding();
        Vec3 rel = point.subtract(box.center());
        Vec3 q = new Vec3(Math.abs(rel.x) - core, Math.abs(rel.y) - core, Math.abs(rel.z) - core);
        Vec3 outside = new Vec3(Math.max(q.x, 0), Math.max(q.y, 0), Math.max(q.z, 0));
        Vec3 facing;
        if (outside.lengthSqr() > 0) {
            facing = new Vec3(Math.signum(rel.x) * outside.x, Math.signum(rel.y) * outside.y,
                    Math.signum(rel.z) * outside.z).normalize();
        } else if (q.x >= q.y && q.x >= q.z) {
            facing = new Vec3(Math.signum(rel.x), 0, 0);
        } else if (q.y >= q.z) {
            facing = new Vec3(0, Math.signum(rel.y), 0);
        } else {
            facing = new Vec3(0, 0, Math.signum(rel.z));
        }
        return new Reading(scaled(box.signedDistance(point), box.half()), facing);
    }

    /**
     * The signed distance to the hull of two spheres, the ring at each end of
     * a segment: a cone tangent to both, so where a stream narrows the wider
     * ring's sphere never stands proud of the segment after it; a capsule
     * whose radius was blended along its line left that sphere's cap bulging
     * past the narrower segment at each ring.
     * decision diagnose-then-fix-unmake-seam-and-ribbing
     *
     * @param px    the point less the first ring's middle, x
     * @param py    its y
     * @param pz    its z
     * @param ax    the second ring's middle less the first's, x
     * @param ay    its y
     * @param az    its z
     * @param first the first ring's radius
     * @param last  the second ring's radius
     * @return the signed distance, below zero inside
     */
    static double roundCone(double px, double py, double pz, double ax, double ay, double az, double first,
                            double last) {
        double length = ax * ax + ay * ay + az * az;
        double fall = first - last;
        double slant = length - fall * fall;
        double toFirst = Math.sqrt(px * px + py * py + pz * pz) - first;
        if (slant <= 0) {
            return first >= last ? toFirst : Math.sqrt(square(px - ax) + square(py - ay) + square(pz - az)) - last;
        }
        double along = px * ax + py * ay + pz * az;
        double past = along - length;
        double sx = px * length - ax * along;
        double sy = py * length - ay * along;
        double sz = pz * length - az * along;
        double side = sx * sx + sy * sy + sz * sz;
        double alongSquared = along * along * length;
        double pastSquared = past * past * length;
        double edge = Math.signum(fall) * fall * fall * side;
        if (Math.signum(past) * slant * pastSquared > edge) {
            return Math.sqrt(side + pastSquared) / length - last;
        }
        if (Math.signum(along) * slant * alongSquared < edge) {
            return toFirst;
        }
        return (Math.sqrt(side * slant / length) + along * fall) / length - first;
    }

    private static double square(double value) {
        return value * value;
    }

    /**
     * The field of a drink at a point: every stream within reach of it
     * unioned smoothly at once, each counting by how near it is, so the skin
     * is one surface however many streams crowd a place and streams meeting
     * merge as metaballs do. A blend of only the two nearest streams jumped
     * wherever which two were nearest changed, cracking and seaming the skin
     * all through a crowded cone.
     *
     * @param skeletons the drink's skeletons
     * @param point     the point
     * @return the field there
     */
    public static double valueAt(List<Skeleton> skeletons, Vec3 point) {
        double sum = 0;
        for (Skeleton skeleton : skeletons) {
            sum += weightOf(skeleton.readingAt(point).distance());
        }
        return falloff(unionOf(sum));
    }

    /**
     * @param distance a stream's stretched signed distance at a point
     * @return how much it counts toward the smooth union there, nothing at the reach and beyond
     */
    static double weightOf(double distance) {
        return Math.max(0, Math.exp(-distance / SOFTNESS) - Math.exp(-REACH / SOFTNESS));
    }

    /**
     * @param weights the streams' weights at a point, summed
     * @return the union's stretched signed distance there: a lone stream's own, the reach where none counts, and
     *         nearer as more streams crowd in
     */
    static double unionOf(double weights) {
        return -SOFTNESS * Math.log(weights + Math.exp(-REACH / SOFTNESS));
    }
}
