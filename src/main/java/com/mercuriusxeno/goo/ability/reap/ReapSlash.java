package com.mercuriusxeno.goo.ability.reap;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * One of Reap's slashes, the Crissaegrim's from Symphony of the Night, the
 * one shape the server cuts with and the client draws. The game draws its
 * slash as one fixed crescent sprite in front of Alucard and lights it by
 * cycling its palette, a bright band sweeping the arc from one tip to the
 * other and the arc wiping away behind it, the sprite itself never moving
 * (decompiled weapon 12, EntityWeaponAttack). Here the crescent is an arc
 * around a hub below the slasher's eye, standing a radius ahead along the
 * look and facing the slasher, thickest mid-arc and tapering to tips that
 * meet. A point on it is named by how far along the arc it lies, 0 at the
 * tip the sweep starts from and 1 at the other, and by its layer, 1 on the
 * outer edge and 0 on the inner.
 * reap-is-crissaegrim-slashes-that-harvest-and-cut
 *
 * @param hub       the point the arc curves around, below the slasher's eye
 * @param forward   the slasher's unit look, which the arc's middle stands along
 * @param across    the unit axis the arc spreads along, the look's right tilted by the slash's form
 * @param radius    how far the outer edge stands from the hub, in blocks
 * @param span      the angle the arc spans, in radians
 * @param thickness how thick its middle is, in blocks
 * @param sweep     which way the band sweeps: 1 from the negative end of the across axis, -1 from the positive
 * @param lifeTicks the ticks from the slash appearing to its wiping away
 */
public record ReapSlash(Vec3 hub, Vec3 forward, Vec3 across, double radius, double span, double thickness,
                        int sweep, int lifeTicks) {

    /** The share of its life the bright band takes to sweep the whole arc. */
    static final double HEAD_SHARE = 0.6;
    /** The share of its life before the arc starts wiping away behind the band. */
    static final double TAIL_START = 0.4;
    /** How far along the radius the box a slash cuts stands centred. */
    static final double BOX_REACH = 0.85;
    private static final double HALF = 0.5;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final Vec3 EAST = new Vec3(1, 0, 0);
    /** Below this squared length a look runs straight up or down and has no level right. */
    private static final double VERTICAL_LOOK = 1e-6;

    /** Network codec, so the client draws the slash the server cuts with. */
    public static final StreamCodec<ByteBuf, ReapSlash> STREAM_CODEC = StreamCodec.composite(
            Vec3.STREAM_CODEC, ReapSlash::hub,
            Vec3.STREAM_CODEC, ReapSlash::forward,
            Vec3.STREAM_CODEC, ReapSlash::across,
            ByteBufCodecs.DOUBLE, ReapSlash::radius,
            ByteBufCodecs.DOUBLE, ReapSlash::span,
            ByteBufCodecs.DOUBLE, ReapSlash::thickness,
            ByteBufCodecs.VAR_INT, ReapSlash::sweep,
            ByteBufCodecs.VAR_INT, ReapSlash::lifeTicks,
            ReapSlash::new);

    /**
     * A slash's size and timing.
     *
     * @param radius    how far its outer edge stands from the hub, in blocks
     * @param span      the angle its arc spans, in radians
     * @param thickness how thick its middle is, in blocks
     * @param drop      how far below the eye the hub stands, in blocks
     * @param tilt      how far a diagonal form slopes from flat, in radians
     * @param lifeTicks the ticks from the slash appearing to its wiping away
     */
    public record Shape(double radius, double span, double thickness, double drop, double tilt, int lifeTicks) {
    }

    /**
     * A slash appearing ahead of the slasher's eye, in a form.
     *
     * @param eye   the slasher's eye
     * @param look  the slasher's unit look
     * @param form  the form it takes
     * @param shape its size and timing
     * @return the slash
     */
    public static ReapSlash launch(Vec3 eye, Vec3 look, SlashForm form, Shape shape) {
        Vec3 level = look.cross(UP);
        Vec3 right = level.lengthSqr() > VERTICAL_LOOK ? level.normalize() : EAST;
        Vec3 up = right.cross(look).normalize();
        Vec3 across = right.scale(Math.cos(shape.tilt() * Math.abs(form.slope())))
                .add(up.scale(Math.sin(shape.tilt()) * form.slope()));
        return new ReapSlash(eye.subtract(up.scale(shape.drop())), look, across, shape.radius(), shape.span(),
                shape.thickness(), form.sweep(), shape.lifeTicks());
    }

    /**
     * A point on the crescent, which stands fixed for the slash's whole life.
     *
     * @param along how far along the arc, 0 at the tip the sweep starts from and 1 at the other
     * @param layer its layer, 1 on the outer edge and 0 on the inner
     * @return the point
     */
    public Vec3 pointAt(double along, double layer) {
        double angle = sweep * (along - HALF) * span;
        double reach = radius - thickness * Math.sin(Math.PI * along) * (1 - layer);
        return hub.add(forward.scale(reach * Math.cos(angle))).add(across.scale(reach * Math.sin(angle)));
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
     * The box the slash cuts: as wide and as deep as named and as tall as
     * named, centred on the hub's level a little short of the crescent's
     * middle, so its top stands below the eye.
     *
     * @param width  its width and depth, in blocks
     * @param height its height, in blocks
     * @return the box
     */
    public AABB box(double width, double height) {
        Vec3 centre = hub.add(forward.scale(radius * BOX_REACH));
        return AABB.ofSize(centre, width, height, width);
    }
}
