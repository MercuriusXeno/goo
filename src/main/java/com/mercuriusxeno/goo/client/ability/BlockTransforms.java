package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayList;
import java.util.List;

/**
 * The block transforms playing on the client: each old block mingling into
 * the new one it became over a second, the template any block-to-block
 * transform draws through (decision petrify-stone-encasement-and-calcify-map).
 * A new transform on a block replaces the one playing there.
 */
public final class BlockTransforms {

    /** Game ticks a transform plays over. */
    public static final int DURATION_TICKS = 20;

    /** The client's transforms. */
    public static final BlockTransforms CLIENT = new BlockTransforms();

    private final List<Transform> playing = new ArrayList<>();

    /**
     * One block mingling from its old state into its new.
     *
     * @param pos   the block
     * @param from  the state it was
     * @param start the game tick the transform began
     */
    public record Transform(BlockPos pos, BlockState from, long start) {

        /**
         * How far the transform has run.
         *
         * @param gameTime the game time including the partial tick
         * @return the share run, from 0 to 1
         */
        public float progress(float gameTime) {
            return Math.clamp((gameTime - start) / DURATION_TICKS, 0f, 1f);
        }
    }

    /**
     * Starts a block's transform, replacing any playing on it.
     *
     * @param pos   the block
     * @param from  the state it was
     * @param start the game tick the transform began
     */
    public void start(BlockPos pos, BlockState from, long start) {
        playing.removeIf(transform -> transform.pos().equals(pos));
        playing.add(new Transform(pos.immutable(), from, start));
    }

    /**
     * The transforms still playing, the ended ones dropped.
     *
     * @param tick the game tick
     * @return the transforms playing
     */
    public List<Transform> live(long tick) {
        playing.removeIf(transform -> tick - transform.start() >= DURATION_TICKS);
        return List.copyOf(playing);
    }

    /** Drops every transform, as a disconnect does. */
    public void clear() {
        playing.clear();
    }
}
