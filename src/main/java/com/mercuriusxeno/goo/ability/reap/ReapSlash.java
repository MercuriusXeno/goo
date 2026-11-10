package com.mercuriusxeno.goo.ability.reap;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * One of Reap's slashes, the Crissaegrim's from Symphony of the Night, the
 * one shape the server cuts with and the client draws. The game draws its
 * slash as one fixed crescent sprite in front of Alucard and lights it by
 * cycling its palette, a bright band sweeping the arc from one tip to the
 * other and the arc wiping away behind it, the sprite itself never moving
 * (decompiled weapon 12, EntityWeaponAttack). Here the crescent stands
 * upright across the slasher's view, facing them like the sprite faces the
 * screen: an arc over a chord along its across axis, bowing along its rise
 * axis, thickest mid-arc and tapering to tips that meet, its tips curling a
 * little back toward the slasher. A point on it is named by how far along the
 * arc it lies, 0 at the tip the sweep starts from and 1 at the other, and by
 * its layer, 1 on the outer edge and 0 on the inner.
 * reap-is-crissaegrim-slashes-that-harvest-and-cut
 *
 * @param eye       the slasher's eye at the slash
 * @param centre    the middle of the arc's chord
 * @param forward   the slasher's unit look, which the crescent faces back along
 * @param across    the unit axis its chord runs along, square to the look and tilted by the slash
 * @param rise      the unit axis it bows along, square to the look and the across axis
 * @param width     its chord's length, tip to tip, in blocks
 * @param thickness how thick its middle is, in blocks
 * @param sweep     which way the band sweeps: 1 from the negative end of the across axis, -1 from the positive
 * @param lifeTicks the ticks from the slash appearing to its wiping away
 */
public record ReapSlash(Vec3 eye, Vec3 centre, Vec3 forward, Vec3 across, Vec3 rise, double width,
                        double thickness, int sweep, int lifeTicks) {

    /** The share of its life the bright band takes to sweep the whole arc. */
    static final double HEAD_SHARE = 0.6;
    /** The share of its life before the arc starts wiping away behind the band. */
    static final double TAIL_START = 0.4;
    /** How far the arc bows from its chord, as a share of its width. */
    static final double SAG_SHARE = 0.18;
    /** How far its tips curl back toward the slasher, as a share of its width. */
    static final double CURL_SHARE = 0.15;
    private static final double HALF = 0.5;
    /** A sweep starting from the negative end of the across axis. */
    private static final int FROM_NEGATIVE_END = 1;
    /** A sweep starting from the positive end of the across axis. */
    private static final int FROM_POSITIVE_END = -FROM_NEGATIVE_END;
    private static final double SPAN = 2;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final Vec3 EAST = new Vec3(1, 0, 0);
    /** Below this squared length a look runs straight up or down and has no level right. */
    private static final double VERTICAL_LOOK = 1e-6;
    /** Network codec, so the client draws the slash the server cuts with. */
    public static final StreamCodec<ByteBuf, ReapSlash> STREAM_CODEC = StreamCodec.composite(
            Vec3.STREAM_CODEC, ReapSlash::eye,
            Vec3.STREAM_CODEC, ReapSlash::centre,
            Vec3.STREAM_CODEC, ReapSlash::forward,
            Vec3.STREAM_CODEC, ReapSlash::across,
            Vec3.STREAM_CODEC, ReapSlash::rise,
            ByteBufCodecs.DOUBLE, ReapSlash::width,
            ByteBufCodecs.DOUBLE, ReapSlash::thickness,
            ByteBufCodecs.VAR_INT, ReapSlash::sweep,
            ByteBufCodecs.VAR_INT, ReapSlash::lifeTicks,
            ReapSlash::new);

    /**
     * The sizes and timing slashes are drawn from.
     *
     * @param distance  how far out along the look a slash stands, in blocks
     * @param narrowest the smallest slash's width, in blocks
     * @param widest    the largest slash's width, in blocks
     * @param thinnest  the smallest slash's thickness, in blocks
     * @param thickest  the largest slash's thickness, in blocks
     * @param sideways  the farthest a slash stands to either side of the aim, in blocks
     * @param drop      how far under the aim a slash's middle stands, in blocks
     * @param maxTilt   the farthest a slash tilts from level either way, in radians
     * @param lifeTicks the ticks from a slash appearing to its wiping away
     */
    public record Shape(double distance, double narrowest, double widest, double thinnest, double thickest,
                        double sideways, double drop, double maxTilt, int lifeTicks) {
    }

    /**
     * A slash appearing ahead of the slasher's eye: standing upright across
     * the view out along the look, its middle just under the aim and a random
     * way to the side within the shape's reach, tilted a random way within the
     * shape's tilt, swept from a random tip, and sized at random, its width
     * and thickness drawn together so a narrow slash is thin and a wide one thick.
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
        Vec3 across = right.scale(Math.cos(tilt)).add(up.scale(Math.sin(tilt)));
        Vec3 rise = up.scale(Math.cos(tilt)).subtract(right.scale(Math.sin(tilt)));
        double size = random.nextDouble();
        double width = shape.narrowest() + (shape.widest() - shape.narrowest()) * size;
        double thickness = shape.thinnest() + (shape.thickest() - shape.thinnest()) * size;
        double sideways = (random.nextDouble() * SPAN - 1) * shape.sideways();
        int sweep = random.nextBoolean() ? FROM_NEGATIVE_END : FROM_POSITIVE_END;
        Vec3 middle = eye.add(look.scale(shape.distance())).add(right.scale(sideways)).subtract(up.scale(shape.drop()));
        Vec3 centre = middle.subtract(rise.scale(width * SAG_SHARE - thickness * HALF));
        return new ReapSlash(eye, centre, look, across, rise, width, thickness, sweep, shape.lifeTicks());
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
     * A point on the crescent, which stands fixed for the slash's whole life.
     *
     * @param along how far along the arc, 0 at the tip the sweep starts from and 1 at the other
     * @param layer its layer, 1 on the outer edge and 0 on the inner
     * @return the point
     */
    public Vec3 pointAt(double along, double layer) {
        double place = sweep > 0 ? along : 1 - along;
        double offCentre = place * SPAN - 1;
        double bow = width * SAG_SHARE * (1 - offCentre * offCentre) - thickness * Math.sin(Math.PI * place) * (1 - layer);
        double curl = width * CURL_SHARE * offCentre * offCentre;
        return centre.add(across.scale(offCentre * width * HALF)).add(rise.scale(bow)).subtract(forward.scale(curl));
    }

    /**
     * How far along the arc the bright band has swept an age into the slash.
     *
     * @param age ticks since it appeared
     * @return 0 at the first tip to 1 at the other
     */
    public double headAt(double age) {
        return Math.clamp(age / (lifeTicks * HEAD_SHARE), 0, 1);
    }

    /**
     * How far along the arc it has wiped away an age into the slash, from
     * the first tip, behind the band.
     *
     * @param age ticks since it appeared
     * @return 0 at the first tip to 1 at the other
     */
    public double tailAt(double age) {
        return Math.clamp((age - lifeTicks * TAIL_START) / (lifeTicks * (1 - TAIL_START)), 0, 1);
    }

    /**
     * The box the slash cuts: from near to far ahead of the eye along the
     * look, as wide as the slash and as tall as named, around its middle.
     *
     * @param near   how far ahead of the eye it starts, in blocks
     * @param far    how far ahead of the eye it reaches, in blocks
     * @param height its height, in blocks
     * @return the box
     */
    public AABB box(double near, double far, double height) {
        Vec3 right = rightOf(forward);
        Vec3 up = right.cross(forward).normalize();
        Vec3 middle = pointAt(HALF, 1).add(pointAt(HALF, 0)).scale(HALF).subtract(eye);
        double lateral = middle.dot(right);
        double level = middle.dot(up);
        AABB box = null;
        for (double ahead : new double[] {near, far}) {
            for (double side : new double[] {-HALF, HALF}) {
                for (double rise : new double[] {-HALF, HALF}) {
                    Vec3 corner = eye.add(forward.scale(ahead)).add(right.scale(lateral + side * width))
                            .add(up.scale(level + rise * height));
                    AABB point = new AABB(corner, corner);
                    box = box == null ? point : box.minmax(point);
                }
            }
        }
        return box;
    }
}
