package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * One burning Flames jet as the fire shader {@code fire_field.fsh} marches it
 * on the world's 16-texel grid: separate licks of flame and small embers
 * poured down the cone from the spout, out to the reach, which a block the
 * look meets cuts short at its face. Packs the field's uniform block and
 * bounds the box the shader marches inside.
 * flames-draws-as-particle-style-fire
 */
final class FireField {

    /** The uniform block's bytes: eight std140 vec4 rows. */
    static final int BYTES = 128;
    /** How far past the cone's radius the box reaches, so the licks wander and climb inside it. */
    private static final double BOX_WIDENING = 1.6;
    private static final double BOX_MARGIN = 0.6;
    /** Blocks the licks climb for each block they travel; covers fire_field.fsh's rise. */
    private static final double CLIMB_PER_BLOCK = 0.2;
    /** Points along the cone the box is fitted around. */
    private static final int BOX_SAMPLES = 8;
    /** The camera's world position is carried modulo this, so the voxel grid holds to the world's at float precision. */
    private static final double GRID_PERIOD = 16;
    private static final Vec3 WORLD_UP = new Vec3(0, 1, 0);
    private static final Vec3 WORLD_EAST = new Vec3(1, 0, 0);
    /** Below this squared length a direction has no way. */
    private static final double NO_WAY = 1e-6;
    private static final int FLOATS_PER_ROW = 4;
    /** A length the shader reads as no wall. */
    private static final double NONE = -1;

    private final Vec3 apex;
    private final Vec3 axis;
    private final Vec3 side;
    private final double tan;
    private final double reach;
    private final double front;
    private final double tail;
    private final @Nullable Wall wall;

    /**
     * The jet's frame at the spout, camera relative.
     *
     * @param apex the spout
     * @param axis the unit vector the fire pours along
     * @param side the unit direction across it to its side
     * @param tan  the tangent of half the cone's angle
     */
    record Jet(Vec3 apex, Vec3 axis, Vec3 side, double tan) {
    }

    /**
     * Where the look meets a block.
     *
     * @param distance blocks from the spout
     * @param at       the hit point, camera relative
     * @param face     the face the look meets
     */
    record Wall(double distance, Vec3 at, Direction face) {

        /** @return the face's unit normal, out of the block */
        Vec3 normal() {
            return new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
        }
    }

    /**
     * The field of a jet burning the given stretch.
     *
     * @param jet   the jet's frame at the spout
     * @param span  the burning stretch down the cone
     * @param range the stream's reach in open air
     * @param wall  where the look meets a block within the reach, or null
     */
    FireField(Jet jet, FireStream.JetSpan span, double range, @Nullable Wall wall) {
        this.apex = jet.apex();
        this.axis = jet.axis();
        this.side = jet.side();
        this.tan = jet.tan();
        this.wall = wall != null && wall.distance() < range ? wall : null;
        this.reach = this.wall == null ? range : this.wall.distance();
        this.front = Math.min(span.front(), reach);
        this.tail = span.tail();
    }

    /**
     * The unit direction across a jet to its side, level with the world where it can be.
     *
     * @param axis the jet's unit vector
     * @return the side
     */
    static Vec3 sideOf(Vec3 axis) {
        Vec3 level = axis.cross(WORLD_UP);
        return level.lengthSqr() > NO_WAY ? level.normalize() : axis.cross(WORLD_EAST).normalize();
    }

    /** @return the spout, camera relative */
    Vec3 apex() {
        return apex;
    }

    /** @return the unit vector the fire pours along */
    Vec3 axis() {
        return axis;
    }

    /** @return the tangent of half the cone's angle */
    double tan() {
        return tan;
    }

    /** @return blocks from the spout the fire reaches: the range, or the face the look meets */
    double reach() {
        return reach;
    }

    /** @return whether any stretch of fire burns */
    boolean burns() {
        return front > tail;
    }

    /**
     * The box the shader marches inside: around the cone out to its reach,
     * widened past its radius and raised for the climb, so the licks and
     * embers stay inside.
     *
     * @return the low corner and the high corner, camera relative
     */
    Vec3[] box() {
        double[] low = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        double[] high = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (int sample = 0; sample <= BOX_SAMPLES; sample++) {
            double distance = reach * sample / BOX_SAMPLES;
            double reachOut = distance * tan * BOX_WIDENING + BOX_MARGIN;
            Vec3 center = apex.add(axis.scale(distance));
            double[] at = {center.x, center.y, center.z};
            double[] climb = {0, distance * CLIMB_PER_BLOCK, 0};
            for (int index = 0; index < at.length; index++) {
                low[index] = Math.min(low[index], at[index] - reachOut);
                high[index] = Math.max(high[index], at[index] + reachOut + climb[index]);
            }
        }
        return new Vec3[] {vectorOf(low), vectorOf(high)};
    }

    private static Vec3 vectorOf(double[] xyz) {
        int index = 0;
        return new Vec3(xyz[index++], xyz[index++], xyz[index]);
    }

    /**
     * Packs the field's uniform block, eight std140 vec4 rows; must match
     * {@code FireField} in fire_field.fsh.
     *
     * @param out       where the bytes go, at its position
     * @param camera    the camera's world position, for the voxel grid
     * @param zeroToOne whether the device's depth runs 0 to 1
     */
    void pack(ByteBuffer out, Vec3 camera, boolean zeroToOne) {
        ByteBuffer rows = out.order(ByteOrder.nativeOrder());
        Vec3[] box = box();
        row(rows, apex, tan);
        row(rows, axis, reach);
        row(rows, side, tail);
        row(rows, wall == null ? WORLD_UP : wall.normal(), front);
        row(rows, wall == null ? apex : wall.at(), wall == null ? NONE : wall.distance());
        row(rows, box[0], zeroToOne ? 1 : 0);
        row(rows, box[1], 0);
        row(rows, new Vec3(gridOf(camera.x), gridOf(camera.y), gridOf(camera.z)), 0);
    }

    private static double gridOf(double value) {
        return Math.floorMod((long) Math.floor(value), (long) GRID_PERIOD) + value - Math.floor(value);
    }

    private static void row(ByteBuffer rows, Vec3 xyz, double w) {
        float[] values = {(float) xyz.x, (float) xyz.y, (float) xyz.z, (float) w};
        for (int index = 0; index < FLOATS_PER_ROW; index++) {
            rows.putFloat(values[index]);
        }
    }
}
