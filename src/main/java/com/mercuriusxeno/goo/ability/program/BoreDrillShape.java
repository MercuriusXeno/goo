package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.GloveRest;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Bore's drill cone, set once in rock_bore.json: as wide at the blob as its
 * width, as long as its length, tapering to a point, held at its angle off
 * the look, with its past length running beyond the plane square to the look
 * at the crosshair. The drawn drill and the break both read it: where the
 * cone crosses that plane it cuts an ellipse, its cross section, and Bore
 * breaks whatever the ellipse intersects.
 * decision bore-drill-dimensions-drive-the-visuals-and-the-break
 * decision bore-cross-section-is-where-the-angled-drill-meets-the-look-plane
 * decision bore-breaks-what-the-drill-cross-section-intersects
 *
 * @param width      the drill's width at the blob, in blocks
 * @param length     the drill's length from the blob to its point, in blocks
 * @param angle      how far off the sight line the drill is held, in degrees
 * @param pastLength how much of the drill runs past the crosshair's plane, in blocks
 */
public record BoreDrillShape(double width, double length, double angle, double pastLength) {

    /** rock_bore.json's drill, which a bore naming none takes. */
    public static final BoreDrillShape SHIPPED = new BoreDrillShape(0.7, 2, 15, 1);
    /** The widest the drill runs: a block, so it is never wider than a block. */
    private static final double WIDEST = 1;
    /** The steepest the drill is held off the sight line, short of square to it. */
    private static final double STEEPEST_ANGLE = 80;
    /** How far past the plane, along the look, each sample is read, so a section flush on a face counts it. */
    private static final double NUDGE = 1e-4;
    /** Rings and turns the ellipse is sampled at, fine against a block's edge. */
    private static final int RINGS = 6;
    private static final int TURNS = 64;
    private static final double FULL_TURN = 2 * Math.PI;
    private static final double HALF = 0.5;
    private static final Vec3 WORLD_UP = new Vec3(0, 1, 0);
    private static final Vec3 WORLD_SOUTH = new Vec3(0, 0, 1);
    private static final double NEARLY_UPRIGHT = 0.999;

    /**
     * Codec for the drill's four numbers, read flat among the bore step's own fields.
     */
    public static final MapCodec<BoreDrillShape> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.doubleRange(0, WIDEST).optionalFieldOf("drill_width", SHIPPED.width())
                    .forGetter(BoreDrillShape::width),
            Codec.doubleRange(0, Double.MAX_VALUE).optionalFieldOf("drill_length", SHIPPED.length())
                    .forGetter(BoreDrillShape::length),
            Codec.doubleRange(0, STEEPEST_ANGLE).optionalFieldOf("drill_angle", SHIPPED.angle())
                    .forGetter(BoreDrillShape::angle),
            Codec.doubleRange(0, Double.MAX_VALUE).optionalFieldOf("past_length", SHIPPED.pastLength())
                    .forGetter(BoreDrillShape::pastLength)
    ).apply(inst, BoreDrillShape::new));

    /**
     * How fast the cone widens back from its point: its radius over its distance from the point.
     *
     * @return the taper
     */
    public double taper() {
        return width * HALF / length;
    }

    private double squeeze() {
        double slope = taper() * Math.tan(Math.toRadians(angle));
        return 1 - slope * slope;
    }

    /**
     * Half the cross section's measure across the drill's tilt.
     *
     * @return the short half measure, in blocks
     */
    public double shortHalf() {
        return pastLength * taper() / Math.sqrt(squeeze());
    }

    /**
     * Half the cross section's measure along the drill's tilt, stretched as
     * the tilted cone meets the plane at a slant.
     *
     * @return the long half measure, in blocks
     */
    public double longHalf() {
        return pastLength * taper() / (squeeze() * Math.cos(Math.toRadians(angle)));
    }

    /**
     * The unit direction square to the look toward the side of the sight
     * line the blob's rest lies on: the glove hand's side and below, where
     * the drill is turned off the look and the cross section's long axis lies.
     *
     * @param look       the unit look
     * @param gloveRight whether the glove is in the right hand
     * @return the direction
     */
    public static Vec3 towardTheBlob(Vec3 look, boolean gloveRight) {
        Vec3 world = Math.abs(look.dot(WORLD_UP)) > NEARLY_UPRIGHT ? WORLD_SOUTH : WORLD_UP;
        Vec3 right = look.cross(world).normalize();
        Vec3 up = right.cross(look).normalize();
        double side = gloveRight ? GloveRest.TO_THE_GLOVE_SIDE : -GloveRest.TO_THE_GLOVE_SIDE;
        return right.scale(side).subtract(up.scale(GloveRest.BELOW)).normalize();
    }

    /**
     * Every cell the cross section intersects, centered at a point on the
     * plane square to the look; a section lying flush on a face counts the
     * block behind it.
     *
     * @param center     the cross section's center, on the eye line
     * @param look       the unit look
     * @param gloveRight whether the glove is in the right hand
     * @return the cells, the center's first, each once
     */
    public List<BlockPos> cellsCrossedAt(Vec3 center, Vec3 look, boolean gloveRight) {
        Vec3 nudged = center.add(look.scale(NUDGE));
        Set<BlockPos> cells = new LinkedHashSet<>();
        cells.add(BlockPos.containing(nudged));
        for (int ring = 1; ring <= RINGS; ring++) {
            for (Vec3 offset : rimOffsets(look, gloveRight, TURNS, (double) ring / RINGS)) {
                cells.add(BlockPos.containing(nudged.add(offset)));
            }
        }
        return List.copyOf(cells);
    }

    /**
     * Points spaced evenly round the cross section's rim, or round a smaller
     * ellipse of the same shape, as offsets from its center in the plane
     * square to the look.
     *
     * @param look       the unit look
     * @param gloveRight whether the glove is in the right hand
     * @param count      how many points
     * @param share      the share of the cross section's size, 1 for its rim
     * @return the offsets
     */
    public List<Vec3> rimOffsets(Vec3 look, boolean gloveRight, int count, double share) {
        Vec3 along = towardTheBlob(look, gloveRight);
        Vec3 across = look.cross(along).normalize();
        Vec3[] offsets = new Vec3[count];
        for (int turn = 0; turn < count; turn++) {
            double radians = FULL_TURN * turn / count;
            offsets[turn] = along.scale(Math.cos(radians) * longHalf() * share)
                    .add(across.scale(Math.sin(radians) * shortHalf() * share));
        }
        return List.of(offsets);
    }
}
