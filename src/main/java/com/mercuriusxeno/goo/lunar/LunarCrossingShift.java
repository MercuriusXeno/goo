package com.mercuriusxeno.goo.lunar;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import java.util.HashSet;
import java.util.Set;

/**
 * How far a crossing of lunar's seam moves what a client already holds: one LunarSeam period, in chunks, on each axis
 * crossed, so a chunk the client held past the seam is the chunk it now holds inside, and the other way round.
 * decision moon-wraps-at-its-border-like-the-zone
 *
 * @param chunksX the move on x, in chunks
 * @param chunksZ the move on z, in chunks
 */
public record LunarCrossingShift(int chunksX, int chunksZ) {

    private static final int CHUNK_SHIFT = 4;
    /** One LunarSeam period in chunks. */
    public static final int PERIOD_CHUNKS = LunarSeam.PERIOD >> CHUNK_SHIFT;

    /**
     * The shift of a body carried from one position to another across the seam.
     *
     * @param from where the body stood
     * @param to   where the crossing set it
     * @return the shift, one period on each axis crossed
     */
    public static LunarCrossingShift between(Vec3 from, Vec3 to) {
        return new LunarCrossingShift(laps(to.x - from.x) * PERIOD_CHUNKS, laps(to.z - from.z) * PERIOD_CHUNKS);
    }

    private static int laps(double moved) {
        return (int) Math.round(moved / LunarSeam.PERIOD);
    }

    /**
     * Where a chunk held before the crossing stands after it.
     *
     * @param held the chunk before the crossing
     * @return the chunk after it
     */
    public ChunkPos apply(ChunkPos held) {
        return new ChunkPos(held.x() + chunksX, held.z() + chunksZ);
    }

    /**
     * Where a chunk standing after the crossing stood before it.
     *
     * @param landed the chunk after the crossing
     * @return the chunk before it
     */
    public ChunkPos undo(ChunkPos landed) {
        return new ChunkPos(landed.x() - chunksX, landed.z() - chunksZ);
    }

    /**
     * Where a block held before the crossing stands after it.
     *
     * @param held the block before the crossing
     * @return the block after it
     */
    public BlockPos apply(BlockPos held) {
        return held.offset(chunksX << CHUNK_SHIFT, 0, chunksZ << CHUNK_SHIFT);
    }

    /**
     * Where a position held before the crossing stands after it.
     *
     * @param held the position before the crossing
     * @return the position after it
     */
    public Vec3 apply(Vec3 held) {
        return held.add(chunksX << CHUNK_SHIFT, 0.0, chunksZ << CHUNK_SHIFT);
    }

    /**
     * The chunks of the landing view a client already holds: each chunk of the view it held, moved by this shift,
     * that the landing view sees, which is the landing view less the ring the move adds.
     *
     * @param held    the view the client held before the crossing
     * @param landing the view it holds after it
     * @return the landing view's chunks the client moved there
     */
    public Set<ChunkPos> moved(ChunkTrackingView held, ChunkTrackingView landing) {
        Set<ChunkPos> moved = new HashSet<>();
        held.forEach(pos -> {
            ChunkPos landed = apply(pos);
            if (landing.contains(landed)) {
                moved.add(landed);
            }
        });
        return moved;
    }
}
