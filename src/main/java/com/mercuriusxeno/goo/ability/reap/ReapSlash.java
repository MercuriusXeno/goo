package com.mercuriusxeno.goo.ability.reap;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * One of Reap's slashes, the Crissaegrim's from Symphony of the Night made a
 * sweep around the slasher, as the operator drew it: a level, almost circular
 * area with the slasher at its near edge, and a crescent band rippling
 * outward through it. The band's back and front are circles about a source a
 * little ahead of the slasher; both travel outward through its life, the
 * band clipped at its sides by the area's edge and ending as the cap at the
 * far edge. The slash moves with its slasher: it is placed from the
 * slasher's feet each time it is drawn or cuts. It is the one shape the
 * server cuts with and every client draws.
 * reap-is-crissaegrim-slashes-that-harvest-and-cut
 *
 * @param nudge   how far the slash is shifted its own way: x to its right, y up, z ahead, in blocks
 * @param heading the level unit direction it sweeps out along
 * @param roll    how far it leans about its heading, its right side raised for a positive lean, in radians
 * @param height  how high above the slasher's feet its plane stands, in blocks
 * @param shape   its area and the band's travel
 */
public record ReapSlash(Vec3 nudge, Vec3 heading, double roll, double height, Shape shape) {

    /** How far under the slasher's feet a slash still reaches, in blocks. */
    static final double REACH_BELOW = 0.5;
    /** How far over its plane a slash still reaches, in blocks. */
    static final double REACH_ABOVE = 0.5;

    private static final double SPAN = 2;
    private static final int HALVINGS = 48;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final Vec3 EAST = new Vec3(1, 0, 0);
    /** Below this squared length a direction runs straight up or down and has no level right. */
    private static final double VERTICAL = 1e-6;

    /** Network codec, so every client draws the slash the server cuts with. */
    public static final StreamCodec<ByteBuf, ReapSlash> STREAM_CODEC = StreamCodec.composite(
            Vec3.STREAM_CODEC, ReapSlash::nudge,
            Vec3.STREAM_CODEC, ReapSlash::heading,
            ByteBufCodecs.DOUBLE, ReapSlash::roll,
            ByteBufCodecs.DOUBLE, ReapSlash::height,
            Shape.STREAM_CODEC, ReapSlash::shape,
            ReapSlash::new);

    /**
     * A slash's area and its band's travel. The area is an ellipse along the
     * heading with its near end at the slasher; distances from the source
     * run along the plane.
     *
     * @param halfWidth  half the area's width, in blocks
     * @param halfLength half the area's reach ahead, in blocks
     * @param source     how far ahead of the slasher the point the band's circles spread from stands, in blocks
     * @param backFrom   the band's back as it appears, from the source, in blocks
     * @param backTo     the band's back at the end of its life, from the source, in blocks
     * @param frontFrom  the band's front as it appears, from the source, in blocks
     * @param lifeTicks  the ticks from the slash appearing to its dimming away
     */
    public record Shape(double halfWidth, double halfLength, double source, double backFrom, double backTo,
                        double frontFrom, int lifeTicks) {

        /** Network codec. */
        public static final StreamCodec<ByteBuf, Shape> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.DOUBLE, Shape::halfWidth,
                ByteBufCodecs.DOUBLE, Shape::halfLength,
                ByteBufCodecs.DOUBLE, Shape::source,
                ByteBufCodecs.DOUBLE, Shape::backFrom,
                ByteBufCodecs.DOUBLE, Shape::backTo,
                ByteBufCodecs.DOUBLE, Shape::frontFrom,
                ByteBufCodecs.VAR_INT, Shape::lifeTicks,
                Shape::new);

        /**
         * How far from the source the area's edge stands along a direction.
         *
         * @param angle the direction off the heading, rightward positive, in radians
         * @return the distance to the edge, in blocks
         */
        public double edgeAlong(double angle) {
            double sine = Math.sin(angle);
            double cosine = Math.cos(angle);
            double behindCentre = (source - halfLength) / halfLength;
            double squared = sine * sine / (halfWidth * halfWidth) + cosine * cosine / (halfLength * halfLength);
            double linear = SPAN * cosine * behindCentre / halfLength;
            double rooted = Math.sqrt(Math.max(0, linear * linear - SPAN * SPAN * squared * (behindCentre * behindCentre - 1)));
            return (rooted - linear) / (SPAN * squared);
        }

        /**
         * The band an age into the slash's life: its back and front, each
         * travelling evenly outward, the front ending at the area's far edge.
         *
         * @param age ticks since the slash appeared
         * @return the band
         */
        public Band bandAt(double age) {
            double progress = Math.clamp(age / lifeTicks, 0, 1);
            double back = backFrom + (backTo - backFrom) * progress;
            double front = frontFrom + (edgeAlong(0) - frontFrom) * progress;
            return new Band(this, back, front, widestAngle(back));
        }

        /**
         * The direction at which the area's edge comes in to a distance from
         * the source, where a band with that back pinches out.
         *
         * @param distance the distance from the source, in blocks
         * @return the angle off the heading, in radians
         */
        private double widestAngle(double distance) {
            double inside = 0;
            double outside = Math.PI;
            for (int halving = 0; halving < HALVINGS; halving++) {
                double middle = (inside + outside) / SPAN;
                if (edgeAlong(middle) > distance) {
                    inside = middle;
                } else {
                    outside = middle;
                }
            }
            return inside;
        }

        /**
         * Whether the band's front has got to a body: the body stands in the
         * area and no farther from the source than the front, a body beside
         * the slasher reached as the slash appears.
         *
         * @param point where the body stands on the plane
         * @param girth half the body's width, which the area and the front reach out by, in blocks
         * @param age   ticks since the slash appeared
         * @return true once the slash has reached it
         */
        boolean reached(PlanePoint point, double girth, double age) {
            double sideways = point.across() / (halfWidth + girth);
            double lengthways = (point.out() - halfLength) / (halfLength + girth);
            return sideways * sideways + lengthways * lengthways <= 1
                    && Math.hypot(point.across(), point.out() - source) <= bandAt(age).front() + girth;
        }
    }

    /**
     * The band at one moment.
     *
     * @param shape  the slash's area
     * @param back   the band's back, from the source, in blocks
     * @param front  the band's front, from the source, in blocks
     * @param widest the angle off the heading its tips stand at, in radians
     */
    public record Band(Shape shape, double back, double front, double widest) {

        /**
         * A point of the band.
         *
         * @param along how far along the band, 0 at its left tip and 1 at its right
         * @param layer its layer, 0 on the band's back and 1 on its front
         * @return the point on the plane
         */
        public PlanePoint at(double along, double layer) {
            double angle = (along * SPAN - 1) * widest;
            double edge = shape.edgeAlong(angle);
            double near = Math.min(back, edge);
            double reach = near + (Math.min(front, edge) - near) * layer;
            return new PlanePoint(reach * Math.sin(angle), shape.source() + reach * Math.cos(angle));
        }
    }

    /**
     * A point on the slash's plane.
     *
     * @param across to the right of the heading, in blocks
     * @param out    ahead of the slasher along the heading, in blocks
     */
    public record PlanePoint(double across, double out) {
    }

    /**
     * What each slash draws its own placing from.
     *
     * @param drop    how far under the slasher's eye its plane stands, in blocks
     * @param nudge   the farthest it is shifted each of sideways, up and ahead, in blocks
     * @param maxTilt the farthest it leans either way, in radians
     * @param maxFan  the farthest its heading turns off the slasher's facing either way, in radians
     */
    public record Thrown(double drop, double nudge, double maxTilt, double maxFan) {
    }

    /**
     * A slash thrown by a slasher: level, its heading turned a little off
     * the slasher's facing, leant a little and nudged a little, each its own
     * way, so two slashes of one swing never land as one.
     *
     * @param facing    the level unit direction the slasher faces
     * @param eyeHeight how high over the slasher's feet their eye stands, in blocks
     * @param thrown    what the placing is drawn from
     * @param shape     the slash's area and band
     * @param random    the random source
     * @return the slash
     */
    public static ReapSlash launch(Vec3 facing, double eyeHeight, Thrown thrown, Shape shape, RandomSource random) {
        double fan = eitherWay(random) * thrown.maxFan();
        Vec3 heading = facing.scale(Math.cos(fan)).add(rightOf(facing).scale(Math.sin(fan))).normalize();
        double roll = eitherWay(random) * thrown.maxTilt();
        Vec3 nudge = new Vec3(eitherWay(random), eitherWay(random), eitherWay(random)).scale(thrown.nudge());
        return new ReapSlash(nudge, heading, roll, eyeHeight - thrown.drop(), shape);
    }

    private static double eitherWay(RandomSource random) {
        return random.nextDouble() * SPAN - 1;
    }

    /**
     * The level axis to the right of a direction.
     *
     * @param direction the unit direction
     * @return the unit right axis
     */
    private static Vec3 rightOf(Vec3 direction) {
        Vec3 level = direction.cross(UP);
        return level.lengthSqr() > VERTICAL ? level.normalize() : EAST;
    }

    /**
     * A point of the slash in the world an age into its life.
     *
     * @param feet  where its slasher's feet stand now
     * @param plane how high over the feet its plane is drawn: its own height, or 0 to lie on the ground
     * @param age   ticks since it appeared
     * @param along how far along the band, 0 at its left tip and 1 at its right
     * @param layer its layer, 0 on the band's back and 1 on its front
     * @return the point
     */
    public Vec3 pointAt(Vec3 feet, double plane, double age, double along, double layer) {
        return place(feet, plane, shape.bandAt(age).at(along, layer));
    }

    /**
     * A plane point in the world: ahead of the slasher along the heading and
     * to its right, the plane leant about the heading and shifted by the nudge.
     *
     * @param feet  where its slasher's feet stand now
     * @param plane how high over the feet its plane is drawn
     * @param point the point on the plane
     * @return the point in the world
     */
    public Vec3 place(Vec3 feet, double plane, PlanePoint point) {
        Vec3 right = rightOf(heading);
        return feet.add(0, plane + nudge.y + point.across() * Math.sin(roll), 0)
                .add(right.scale(nudge.x + point.across() * Math.cos(roll)))
                .add(heading.scale(nudge.z + point.out()));
    }

    /**
     * Whether the slash has reached a body an age into its life.
     *
     * @param feet  where its slasher's feet stand now
     * @param body  where the body stands
     * @param girth half the body's width, in blocks
     * @param age   ticks since the slash appeared
     * @return true once the band's front has got to the body inside the area
     */
    public boolean reaches(Vec3 feet, Vec3 body, double girth, double age) {
        Vec3 right = rightOf(heading);
        Vec3 from = body.subtract(feet);
        return shape.reached(new PlanePoint(from.dot(right) - nudge.x, from.dot(heading) - nudge.z), girth, age);
    }

    /**
     * The box holding everything the slash can reach: its area's whole
     * reach around the slasher, from a little under the feet to a little
     * over its plane, so it cuts what stands at the slasher's level.
     *
     * @param feet where its slasher's feet stand now
     * @return the box
     */
    public AABB reachBox(Vec3 feet) {
        double around = shape.halfLength() * SPAN + Math.abs(nudge.x) + Math.abs(nudge.z);
        return new AABB(feet.x - around, feet.y - REACH_BELOW, feet.z - around,
                feet.x + around, feet.y + height + REACH_ABOVE, feet.z + around);
    }

    /**
     * How bright the slash is an age into its life: whole as it appears,
     * dimming evenly to nothing.
     *
     * @param age ticks since it appeared
     * @return its strength, 0 to 1
     */
    public float strengthAt(double age) {
        return (float) (1 - Math.clamp(age / shape.lifeTicks(), 0, 1));
    }

    /**
     * The ticks from the slash appearing to its dimming away.
     *
     * @return its life, in ticks
     */
    public int lifeTicks() {
        return shape.lifeTicks();
    }
}
