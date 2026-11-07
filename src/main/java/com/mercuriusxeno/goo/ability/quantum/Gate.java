package com.mercuriusxeno.goo.ability.quantum;

import io.netty.buffer.ByteBuf;
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
