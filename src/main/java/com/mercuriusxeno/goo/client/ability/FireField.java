package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.FlameShape;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * One burning Flames jet as the fire field {@code fire_field.fsh} marches:
 * the jet's cone from the glove, and where a block cuts the look off, the
 * damped bounce off its face, the two merged smoothly into one body of fire
 * that never reaches behind the face. Packs the field's uniform block and
 * bounds the box the shader marches inside. flames-pixelated-fire-stream
 * <p>
 * The operator's rulings on the bounce: the fire keeps its push along the
 * face and bounces damped, as {@link FlameShape} lays out; the server burns
 * with that same shape, so the damage lands where the fire is drawn.
 */
final class FireField {

    private static final double BOUNCE_SPLAY = FlameShape.SPLAY;
    private static final double BOUNCE_RISE = FlameShape.RISE;
    /** The uniform block's bytes: ten std140 vec4 rows. */
    static final int BYTES = 160;
    /** How far past the flame's radius the box reaches, so the flames lick out and climb inside it. */
    private static final double BOX_WIDENING = 1.6;
    private static final double BOX_MARGIN = 0.6;
    /** How many radii under a ceiling the rolling fire's body reaches; covers fire_field.fsh's hang. */
    private static final double CEILING_HANG = 3;
    /** Points along each centerline the box is fitted around. */
    private static final int BOX_SAMPLES = 8;
    /** The camera's world position is carried modulo this, so the voxel grid holds to the world's at float precision. */
    private static final double GRID_PERIOD = 16;
    private static final Vec3 WORLD_UP = new Vec3(0, 1, 0);
    private static final int FLOATS_PER_ROW = 4;
    /** A length the shader reads as no bounce and no wall. */
    private static final double NONE = -1;

    private final Vec3 apex;
    private final Vec3 axis;
    private final Vec3 side;
    private final double tan;
    private final double jetEnd;
    private final double tail;
    private final boolean frontFrays;
    private final @Nullable Wall wall;
    private final Vec3 bounce;
    private final double bounceLength;
    private final Vec3 climb;

    /**
     * The jet's frame at the glove, camera relative.
     *
     * @param apex the glove
     * @param axis the look's unit vector
     * @param side the unit direction across the look to its side
     * @param tan  the tangent of half the cone's angle
     */
    record Jet(Vec3 apex, Vec3 axis, Vec3 side, double tan) {
    }

    /**
     * Where the look hits a block.
     *
     * @param distance blocks from the glove
     * @param at       the hit point, camera relative
     * @param face     the face the look hits
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
     * @param jet  the jet's frame at the glove
     * @param span the burning stretch down the look
     * @param wall where the look hits a block within the reach, or null
     */
    FireField(Jet jet, FireStream.JetSpan span, @Nullable Wall wall) {
        this.apex = jet.apex();
        this.axis = jet.axis();
        this.side = jet.side();
        this.tan = jet.tan();
        // The server burns with the same shape, so the drawn fire is where the damage lands.
        FlameShape shape = new FlameShape(apex, axis, side, tan, span.front(), wall == null ? null : wall.at(),
                wall == null ? null : wall.normal());
        this.wall = shape.bounces() ? wall : null;
        this.jetEnd = shape.jetEnd();
        this.frontFrays = shape.frontFrays();
        this.tail = span.tail();
        this.bounce = shape.bounce();
        this.bounceLength = Math.max(0, shape.bounceLength());
        this.climb = shape.climb();
    }

    /** @return whether any stretch of fire burns */
    boolean burns() {
        return jetEnd > tail || (wall != null && wall.distance() + bounceLength > tail);
    }

    /**
     * A point on the bounce's centerline.
     *
     * @param along blocks along the bounce from the hit point
     * @return the point, camera relative
     */
    Vec3 bouncePoint(double along) {
        Vec3 hit = wall == null ? apex : wall.at();
        return hit.add(bounce.scale(along)).add(climb.scale(BOUNCE_RISE * along * along));
    }

    /**
     * The box the shader marches inside: around both centerlines, widened
     * past the flame's radius so its licks and climb stay inside.
     *
     * @return the low corner and the high corner, camera relative
     */
    Vec3[] box() {
        double[] low = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        double[] high = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (int sample = 0; sample <= BOX_SAMPLES; sample++) {
            double distance = jetEnd * sample / BOX_SAMPLES;
            grow(low, high, apex.add(axis.scale(distance)), distance * tan);
            if (wall != null) {
                double along = bounceLength * sample / BOX_SAMPLES;
                grow(low, high, bouncePoint(along), (wall.distance() + along * BOUNCE_SPLAY) * tan);
            }
        }
        if (wall != null && wall.face() == Direction.DOWN) {
            // Under a ceiling the fire may spread evenly around the hit and hangs its body under the face.
            double widest = (wall.distance() + bounceLength * BOUNCE_SPLAY) * tan;
            grow(low, high, wall.at(), bounceLength + widest * CEILING_HANG);
        }
        return new Vec3[] {vectorOf(low), vectorOf(high)};
    }

    private static Vec3 vectorOf(double[] xyz) {
        int index = 0;
        return new Vec3(xyz[index++], xyz[index++], xyz[index]);
    }

    private static void grow(double[] low, double[] high, Vec3 center, double radius) {
        double reach = radius * BOX_WIDENING + BOX_MARGIN;
        double[] at = {center.x, center.y, center.z};
        for (int axisIndex = 0; axisIndex < at.length; axisIndex++) {
            low[axisIndex] = Math.min(low[axisIndex], at[axisIndex] - reach);
            high[axisIndex] = Math.max(high[axisIndex], at[axisIndex] + reach);
        }
    }

    /**
     * Packs the field's uniform block, ten std140 vec4 rows; must match
     * {@code FireField} in fire_field.fsh.
     *
     * @param out        where the bytes go, at its position
     * @param camera     the camera's world position, for the voxel grid
     * @param zeroToOne  whether the device's depth runs 0 to 1
     */
    void pack(ByteBuffer out, Vec3 camera, boolean zeroToOne) {
        ByteBuffer rows = out.order(ByteOrder.nativeOrder());
        Vec3 hit = wall == null ? apex : wall.at();
        Vec3 normal = wall == null ? WORLD_UP : wall.normal();
        Vec3[] box = box();
        row(rows, apex, tan);
        row(rows, axis, jetEnd);
        row(rows, side, tail);
        row(rows, bounce, wall == null ? NONE : bounceLength);
        row(rows, hit, wall == null ? NONE : wall.distance());
        row(rows, normal, frontFrays ? 1 : 0);
        row(rows, climb, zeroToOne ? 1 : 0);
        row(rows, box[0], 0);
        row(rows, box[1], 0);
        row(rows, new Vec3(Math.floorMod((long) Math.floor(camera.x), (long) GRID_PERIOD) + frac(camera.x),
                Math.floorMod((long) Math.floor(camera.y), (long) GRID_PERIOD) + frac(camera.y),
                Math.floorMod((long) Math.floor(camera.z), (long) GRID_PERIOD) + frac(camera.z)), 0);
    }

    private static double frac(double value) {
        return value - Math.floor(value);
    }

    private static void row(ByteBuffer rows, Vec3 xyz, double w) {
        float[] values = {(float) xyz.x, (float) xyz.y, (float) xyz.z, (float) w};
        for (int index = 0; index < FLOATS_PER_ROW; index++) {
            rows.putFloat(values[index]);
        }
    }
}
