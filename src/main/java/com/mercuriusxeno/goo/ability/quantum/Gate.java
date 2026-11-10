package com.mercuriusxeno.goo.ability.quantum;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * One mouth of a pair the world can be seen through: a portal's frame or one
 * of a hole's mouths. It lies on a face of the blocks it covers, and a view
 * through it is drawn from the camera mirrored onto its partner.
 * portals-link-by-looking-through-portal-vision
 * hole-closes-the-space-to-an-opening-ahead
 *
 * @param dimension the level it stands in
 * @param blocks    the blocks it lies on, as one box one block deep
 * @param face      the face of those blocks it covers
 * @param up        its up, along the face
 */
public record Gate(ResourceKey<Level> dimension, AABB blocks, Direction face, Vec3 up) {

    /** Sends a gate. */
    public static final StreamCodec<ByteBuf, Gate> STREAM_CODEC = StreamCodec.of(Gate::encode, Gate::decode);

    private static final double HALF = 0.5;
    /** The six edges a box is sent as, in the order written. */
    private static final int AABB_EDGES = 6;
    private static final int MIN_X = 0;
    private static final int MIN_Y = 1;
    private static final int MIN_Z = 2;
    private static final int MAX_X = 3;
    private static final int MAX_Y = 4;
    private static final int MAX_Z = 5;

    /**
     * @return the middle of the face it covers
     */
    public Vec3 center() {
        return blocks.getCenter().add(face.getUnitVec3().scale(HALF));
    }

    /**
     * @return its right, along the face, across its up
     */
    public Vec3 right() {
        return up.cross(face.getUnitVec3());
    }

    /**
     * How far a point stands out of the face: positive before it, negative
     * behind it, inside the blocks.
     *
     * @param point a point
     * @return the distance along the face's normal
     */
    public double out(Vec3 point) {
        return point.subtract(center()).dot(face.getUnitVec3());
    }

    /**
     * Whether a point lies within the gate's oval, seen square on.
     *
     * @param point a point
     * @return true inside the oval
     */
    public boolean withinOval(Vec3 point) {
        return withinOval(point, 0);
    }

    /**
     * Whether a point lies within the gate's oval grown by a margin all
     * round, seen square on.
     *
     * @param point  a point
     * @param margin how far past the oval's edge still reads within, in blocks
     * @return true inside the grown oval
     */
    public boolean withinOval(Vec3 point, double margin) {
        Vec3 offset = point.subtract(center());
        double across = offset.dot(right()) / (halfSpan(right()) + margin);
        double along = offset.dot(up) / (halfSpan(up) + margin);
        return across * across + along * along <= 1.0;
    }

    /**
     * Whether a block is one of those the gate lies on.
     *
     * @param pos a block
     * @return true for one of its blocks
     */
    public boolean covers(BlockPos pos) {
        return blocks.contains(Vec3.atCenterOf(pos));
    }

    /**
     * Whether a block frames the gate: one beside or around it in the same
     * wall, floor or ceiling, touching its blocks.
     *
     * @param pos a block
     * @return true for a block of the gate's own layer bordering it
     */
    public boolean frames(BlockPos pos) {
        Vec3 middle = Vec3.atCenterOf(pos);
        return !covers(pos) && blocks.inflate(1.0).contains(middle) && Math.abs(out(middle) + HALF) < HALF;
    }

    /**
     * How far the gate's opening reaches from its middle along an axis across its face.
     *
     * @param axis a unit axis along the face, its right or its up
     * @return the half span, in blocks
     */
    double halfSpan(Vec3 axis) {
        return Math.abs(axis.x()) * blocks.getXsize() * HALF + Math.abs(axis.y()) * blocks.getYsize() * HALF
                + Math.abs(axis.z()) * blocks.getZsize() * HALF;
    }

    private static void encode(ByteBuf buf, Gate gate) {
        ResourceKey.streamCodec(Registries.DIMENSION).encode(buf, gate.dimension());
        AABB box = gate.blocks();
        for (double edge : new double[] {box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ}) {
            ByteBufCodecs.DOUBLE.encode(buf, edge);
        }
        Direction.STREAM_CODEC.encode(buf, gate.face());
        Vec3.STREAM_CODEC.encode(buf, gate.up());
    }

    private static Gate decode(ByteBuf buf) {
        ResourceKey<Level> dimension = ResourceKey.streamCodec(Registries.DIMENSION).decode(buf);
        double[] edges = new double[AABB_EDGES];
        for (int edge = 0; edge < AABB_EDGES; edge++) {
            edges[edge] = ByteBufCodecs.DOUBLE.decode(buf);
        }
        AABB box = new AABB(edges[MIN_X], edges[MIN_Y], edges[MIN_Z], edges[MAX_X], edges[MAX_Y], edges[MAX_Z]);
        return new Gate(dimension, box, Direction.STREAM_CODEC.decode(buf), Vec3.STREAM_CODEC.decode(buf));
    }
}
