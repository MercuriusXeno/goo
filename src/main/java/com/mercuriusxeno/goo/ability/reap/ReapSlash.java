package com.mercuriusxeno.goo.ability.reap;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * One of Reap's slashes, the Crissaegrim's from Symphony of the Night as its
 * frames show it, the one shape the server cuts with and the client draws. In
 * the game each slash is a huge thick crescent standing out ahead of Alucard,
 * hollow toward him and bulging away, at its own angle, appearing whole and
 * bright and then dimming to an afterimage that drifts a little further out.
 * Here the crescent lies nearly flat in a plane along the slasher's look,
 * its chord running along its span axis with its tips out at the near
 * distance and its middle bulging farther out along the look; the plane sits
 * under the line of sight, so the slasher looks down onto its face and the
 * whole of it stays below the eye line. A point on it is named by how far along the arc it
 * lies, 0 at one tip and 1 at the other, and by its layer, 1 on the bulging
 * edge and 0 on the hollow one; the two edges meet at the tips.
 * reap-is-crissaegrim-slashes-that-harvest-and-cut
 *
 * @param origin    the middle of its chord as it appears, out at its tips' distance and off the line of sight
 * @param forward   the slasher's unit look, which it bulges and drifts along
 * @param span      the unit axis its chord runs along, square to the look and nearly level
 * @param width     its chord's length, tip to tip, in blocks
 * @param thickness how thick its middle is, in blocks
 * @param bulge     how far its middle bows out beyond its tips, in blocks
 * @param drift     how far it drifts outward over its life, in blocks
 * @param lifeTicks the ticks from it appearing to its dimming away
 */
public record ReapSlash(Vec3 origin, Vec3 forward, Vec3 span, double width, double thickness, double bulge,
                        double drift, int lifeTicks) {

    /** How far past the crescent the box it cuts reaches, in blocks. */
    static final double BOX_SLACK = 0.3;
    /** The points sampled along the crescent for the box it cuts. */
    private static final int BOX_SAMPLES = 8;
    private static final double HALF = 0.5;

    private static final double SPAN = 2;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final Vec3 EAST = new Vec3(1, 0, 0);
    /** Below this squared length a look runs straight up or down and has no level right. */
    private static final double VERTICAL_LOOK = 1e-6;


    /** Network codec, so the client draws the slash the server cuts with. */
    public static final StreamCodec<ByteBuf, ReapSlash> STREAM_CODEC = StreamCodec.composite(
            Vec3.STREAM_CODEC, ReapSlash::origin,
            Vec3.STREAM_CODEC, ReapSlash::forward,
            Vec3.STREAM_CODEC, ReapSlash::span,
            ByteBufCodecs.DOUBLE, ReapSlash::width,
            ByteBufCodecs.DOUBLE, ReapSlash::thickness,
            ByteBufCodecs.DOUBLE, ReapSlash::bulge,
            ByteBufCodecs.DOUBLE, ReapSlash::drift,
            ByteBufCodecs.VAR_INT, ReapSlash::lifeTicks,
            ReapSlash::new);

    /**
     * The sizes and timing slashes are drawn from.
     *
     * @param near      how far out along the look a slash's tips stand, in blocks
     * @param narrowest the smallest slash's width, in blocks
     * @param widest    the largest slash's width, in blocks
     * @param thinnest  the smallest slash's thickness, in blocks
     * @param thickest  the largest slash's thickness, in blocks
     * @param bulge     how far its middle bows out beyond its tips, as a share of its width
     * @param dropLeast the least its plane stands under the line of sight, in blocks
     * @param dropMost  the most its plane stands under the line of sight, in blocks
     * @param maxTilt   the farthest it tilts from flat either way, in radians
     * @param drift     how far it drifts outward over its life, in blocks
     * @param lifeTicks the ticks from it appearing to its dimming away
     */
    public record Shape(double near, double narrowest, double widest, double thinnest, double thickest,
                        double bulge, double dropLeast, double dropMost, double maxTilt, double drift,
                        int lifeTicks) {
    }

    /**
     * A slash appearing out ahead of the slasher: its plane along the look,
     * tilted at random within the shape's tilt of flat and set under the line
     * of sight by a random drop, so the slasher looks down onto its face and
     * the whole of it stays below the eye line; sized at random, its width,
     * thickness and bulge drawn together so a small slash is narrow and thin
     * and a large one wide, thick and deep.
     *
     * @param eye    the slasher's eye
     * @param look   the slasher's unit look
     * @param shape  the sizes and timing slashes are drawn from
     * @param random the random source
     * @return the slash
     */
    public static ReapSlash launch(Vec3 eye, Vec3 look, Shape shape, RandomSource random) {
        Vec3 right = rightOf(look);
        Vec3 up = right.cross(look).normalize();
        double tilt = (random.nextDouble() * SPAN - 1) * shape.maxTilt();
        Vec3 span = right.scale(Math.cos(tilt)).add(up.scale(Math.sin(tilt)));
        Vec3 normal = look.cross(span).normalize();
        Vec3 under = normal.dot(up) > 0 ? normal.reverse() : normal;
        double drop = shape.dropLeast() + (shape.dropMost() - shape.dropLeast()) * random.nextDouble();
        double size = random.nextDouble();
        double width = shape.narrowest() + (shape.widest() - shape.narrowest()) * size;
        double thickness = shape.thinnest() + (shape.thickest() - shape.thinnest()) * size;
        Vec3 origin = eye.add(look.scale(shape.near())).add(under.scale(drop));
        return new ReapSlash(origin, look, span, width, thickness, width * shape.bulge(), shape.drift(),
                shape.lifeTicks());
    }
    /**
     * The level axis to the right of a look.
     *
     * @param look the unit look
     * @return the unit right axis
     */
    private static Vec3 rightOf(Vec3 look) {
        Vec3 level = look.cross(UP);
        return level.lengthSqr() > VERTICAL_LOOK ? level.normalize() : EAST;
    }

    /**
     * A point on the crescent an age into its life, drifted outward by then.
     *
     * @param age   ticks since it appeared
     * @param along how far along the arc, 0 at one tip and 1 at the other
     * @param layer its layer, 1 on the bulging edge and 0 on the hollow one
     * @return the point
     */
    public Vec3 pointAt(double age, double along, double layer) {
        double offCentre = along * SPAN - 1;
        double out = bulge * (1 - offCentre * offCentre) - thickness * Math.sin(Math.PI * along) * (1 - layer);
        double drifted = drift * Math.clamp(age / lifeTicks, 0, 1);
        return origin.add(span.scale(offCentre * width * HALF)).add(forward.scale(out + drifted));
    }

    /**
     * How bright the slash is an age into its life: whole as it appears,
     * dimming evenly to nothing.
     *
     * @param age ticks since it appeared
     * @return its strength, 0 to 1
     */
    public float strengthAt(double age) {
        return (float) (1 - Math.clamp(age / lifeTicks, 0, 1));
    }

    /**
     * The box the slash cuts: around the whole crescent from where it
     * appears to where it has drifted, a little past it.
     *
     * @return the box
     */
    public AABB box() {
        AABB box = new AABB(origin, origin);
        for (double age : new double[] {0, lifeTicks}) {
            for (int step = 0; step <= BOX_SAMPLES; step++) {
                for (double layer : new double[] {0, 1}) {
                    Vec3 point = pointAt(age, (double) step / BOX_SAMPLES, layer);
                    box = box.minmax(new AABB(point, point));
                }
            }
        }
        return box.inflate(BOX_SLACK);
    }
}
