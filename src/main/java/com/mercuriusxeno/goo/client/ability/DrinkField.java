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
    /** The fillet where a stream leaves its block's box, in blocks, so the mouth shows no crease. */
    public static final double BOX_BLEND = 0.35;
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
         * @return how far the segment's surface stands off its straight line at most: its wider ring's radius and
         *         how far its curve bows out of the line, a third of its length times the sine of the steeper of its
         *         two tangents off the line
         */
        double swellOf(int segment) {
            DrinkStream.Ring from = rings.get(segment);
            DrinkStream.Ring to = rings.get(segment + 1);
            Vec3 line = to.center().subtract(from.center());
            double chord = line.length();
            if (chord == 0) {
                return Math.max(from.radius(), to.radius());
            }
            Vec3 along = line.scale(1 / chord);
            double steepest = Math.max(tangentAt(segment).cross(along).length(),
                    tangentAt(segment + 1).cross(along).length());
            return Math.max(from.radius(), to.radius()) + chord / THREE * steepest;
        }

        /**
         * @param point a point
         * @return the stream's reading there: the least of the segments owning the point, or of their round cones
         *         where none does, and its box filleted onto them; {@link #REACH} away where it has no body
         */
        Reading readingAt(Vec3 point) {
            Reading owned = null;
            Reading unowned = new Reading(REACH, UP);
            for (int segment = 0; segment < segments(); segment++) {
                Reading reading = curveReading(segment, point);
                if (reading != null) {
                    owned = owned == null ? reading : nearer(owned, reading);
                } else {
                    unowned = nearer(unowned, segmentReading(rings.get(segment), rings.get(segment + 1), point));
                }
            }
            return withBox(owned == null ? unowned : nearer(new Reading(REACH, UP), owned), point);
        }

        /**
         * @param tube  the stream's tube's reading at a point
         * @param point the point
         * @return the tube with the block's box filleted onto it while the block stands, so its mouth shows no crease
         */
        private Reading withBox(Reading tube, Vec3 point) {
            if (box == null) {
                return tube;
            }
            Reading boxed = boxReading(box, point);
            return tube.distance() >= REACH ? nearer(tube, boxed) : blend(tube, boxed, BOX_BLEND);
        }

        /**
         * @param segment a segment
         * @param before  the point's distance past the plane at its first ring
         * @param after   the point's distance past the plane at its second ring
         * @return whether the segment owns the point: between the planes, or before the first segment's or past the
         *         last segment's
         */
        private boolean owns(int segment, double before, double after) {
            return (before >= 0 || segment == 0) && (after <= 0 || segment == segments() - 1);
        }

        /**
         * @param ring a ring's index
         * @return the unit direction the skeleton runs through it, from the ring before it to the ring after it, so
         *         the two segments meeting there share it
         */
        Vec3 tangentAt(int ring) {
            Vec3 run = rings.get(Math.min(ring + 1, rings.size() - 1)).center()
                    .subtract(rings.get(Math.max(ring - 1, 0)).center());
            return run.lengthSqr() > 0 ? run.normalize() : UP;
        }

        /**
         * @param ring a ring's index
         * @return how fast the radius changes through it per segment, from the ring before it to the ring after it
         */
        double radiusSlopeAt(int ring) {
            int before = Math.max(ring - 1, 0);
            int after = Math.min(ring + 1, rings.size() - 1);
            return after == before ? 0 : (rings.get(after).radius() - rings.get(before).radius()) / (after - before);
        }

        /**
         * A point's reading off the smooth tube through one segment: the
         * segment owns the points between the planes square to the skeleton's
         * run at its two rings, so a ring's plane parts its two segments and
         * both read the same there, value and facing alike; a point's foot is
         * its share of the way between the planes, on the curve leaving each
         * ring along the run there, its radius easing between the rings the
         * same way. A tube of straight segments creased its outer side at
         * every ring where the snake bends, three to the block.
         * decision diagnose-then-fix-unmake-seam-and-ribbing
         *
         * @param segment the segment
         * @param point   the point
         * @return the reading, or null where the segment does not own the point; the first and the last segment
         *         own the points before and past the skeleton's ends, read off the end ring's sphere
         */
        @Nullable Reading curveReading(int segment, Vec3 point) {
            DrinkStream.Ring from = rings.get(segment);
            DrinkStream.Ring to = rings.get(segment + 1);
            Vec3 leaving = tangentAt(segment);
            Vec3 arriving = tangentAt(segment + 1);
            double before = point.subtract(from.center()).dot(leaving);
            double after = point.subtract(to.center()).dot(arriving);
            if (!owns(segment, before, after)) {
                return null;
            }
            double t = footShare(before, after);
            double chord = to.center().distanceTo(from.center());
            Vec3 foot = hermite(from.center(), to.center(), leaving.scale(chord), arriving.scale(chord), t);
            double radius = Math.clamp(hermite(from.radius(), to.radius(), radiusSlopeAt(segment),
                    radiusSlopeAt(segment + 1), t), Math.min(from.radius(), to.radius()),
                    Math.max(from.radius(), to.radius()));
            Vec3 off = point.subtract(foot);
            double length = off.length();
            double phase = from.spin() + (to.spin() - from.spin()) * t;
            double skin = radius * grooved(leaving.lerp(arriving, t), off, phase);
            return new Reading(scaled(length - skin, radius), length > 0 ? off.scale(1 / length) : UP);
        }

        /**
         * @param axis  the stream's run at a point's foot
         * @param off   the point less its foot
         * @param phase the cyclone's phase there
         * @return the share of the radius the cyclone's skin stands at in the point's direction, its grooves fading
         *         toward the run's own line past an end of the stream, where no angle about the run is had
         */
        double grooved(Vec3 axis, Vec3 off, double phase) {
            Vec3 run = axis.lengthSqr() > 0 ? axis.normalize() : UP;
            double length = off.lengthSqr();
            double square = length == 0 ? 0 : off.subtract(run.scale(off.dot(run))).lengthSqr() / length;
            return 1 - (1 - DrinkStream.cycloneAt(angleAbout(run, off), phase)) * square;
        }

        /**
         * @param axis the stream's run at a point's foot
         * @param off  the point less its foot
         * @return the point's angle about the run, in radians, from the stream's side laid square to the run
         */
        double angleAbout(Vec3 axis, Vec3 off) {
            Vec3 run = axis.lengthSqr() > 0 ? axis.normalize() : UP;
            Vec3 side = DrinkStream.sideOf(stream.path());
            Vec3 across = side.subtract(run.scale(side.dot(run)));
            if (across.lengthSqr() == 0) {
                return 0;
            }
            Vec3 first = across.normalize();
            return Math.atan2(off.dot(run.cross(first)), off.dot(first));
        }
    }

    /**
     * @param before a point's distance past the plane at a segment's first ring
     * @param after  its distance past the plane at the second
     * @return its foot's share of the segment: its share of the way between the planes, 0 before the first and 1
     *         past the second
     */
    static double footShare(double before, double after) {
        if (before <= 0) {
            return 0;
        }
        return after >= 0 ? 1 : before / (before - after);
    }

    /**
     * @param start      the value at the start
     * @param end        the value at the end
     * @param startSlope its rate of change at the start, per unit of t
     * @param endSlope   its rate at the end
     * @param t          0 at the start to 1 at the end
     * @return the cubic Hermite curve between them at t
     */
    static double hermite(double start, double end, double startSlope, double endSlope, double t) {
        double t2 = t * t;
        double t3 = t2 * t;
        return (TWO * t3 - THREE * t2 + 1) * start + (t3 - TWO * t2 + t) * startSlope
                + (THREE * t2 - TWO * t3) * end + (t3 - t2) * endSlope;
    }

    private static Vec3 hermite(Vec3 start, Vec3 end, Vec3 startSlope, Vec3 endSlope, double t) {
        return new Vec3(hermite(start.x, end.x, startSlope.x, endSlope.x, t),
                hermite(start.y, end.y, startSlope.y, endSlope.y, t),
                hermite(start.z, end.z, startSlope.z, endSlope.z, t));
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
     * The smooth union of two readings: the nearer, less a fillet where the
     * two come within its width of each other, its facing turning between
     * theirs across the fillet so the union has no crease.
     *
     * @param a     a reading
     * @param b     another
     * @param width the fillet's width, in blocks; none takes the nearer whole
     * @return the union
     */
    static Reading blend(Reading a, Reading b, double width) {
        double apart = Math.abs(a.distance() - b.distance());
        if (width <= 0 || apart >= width) {
            return nearer(a, b);
        }
        double h = (width - apart) / width;
        double towardA = Math.clamp((1 + (b.distance() - a.distance()) / width) / TWO, 0, 1);
        Vec3 facing = a.facing().scale(towardA).add(b.facing().scale(1 - towardA));
        return new Reading(Math.min(a.distance(), b.distance()) - h * h * width / FOUR,
                facing.lengthSqr() > 0 ? facing.normalize() : a.facing());
    }

    private static Reading nearer(Reading a, Reading b) {
        return b.distance() < a.distance() ? b : a;
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
