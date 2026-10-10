package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The block transforms playing on the client: each old block mingling into
 * the new one it became over a second, the template any block-to-block
 * transform draws through (decision petrify-stone-encasement-and-calcify-map).
 * A new transform on a block replaces the one playing there. Beside them
 * stand the blocks part way to their next calcify rung, each drawing the next
 * block mingled in by its share until the share clears or stops arriving.
 */
public final class BlockTransforms {

    /** Game ticks a transform plays over. */
    public static final int DURATION_TICKS = 20;

    /** The client's transforms. */
    public static final BlockTransforms CLIENT = new BlockTransforms();

    /** Game ticks an exposure stands with no update before it is dropped. */
    static final int EXPOSURE_STALE_TICKS = 40;

    private final List<Transform> playing = new ArrayList<>();
    private final Map<BlockPos, Exposure> exposures = new HashMap<>();

    /**
     * A block part way to its next calcify rung.
     *
     * @param pos     the block
     * @param toward  the state it calcifies into
     * @param share   the share built, 0 to 1
     * @param updated the game tick the share last arrived
     * @param tint    the RGB the mingled block is tinted by, negative for none
     */
    public record Exposure(BlockPos pos, BlockState toward, float share, long updated, int tint) {
    }

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
        exposures.remove(pos);
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

    /**
     * Sets a block's share toward its next rung; a zero share clears it.
     *
     * @param pos    the block
     * @param toward the state it calcifies into
     * @param share  the share built
     * @param tick   the game tick it arrived
     * @param tint   the RGB the mingled block is tinted by, negative for none
     */
    public void expose(BlockPos pos, BlockState toward, float share, long tick, int tint) {
        if (share <= 0f) {
            exposures.remove(pos);
        } else {
            exposures.put(pos.immutable(), new Exposure(pos.immutable(), toward, share, tick, tint));
        }
    }

    /**
     * The blocks part way to their next rung, the stale ones dropped.
     *
     * @param tick the game tick
     * @return the exposures standing
     */
    public List<Exposure> exposing(long tick) {
        exposures.values().removeIf(exposure -> tick - exposure.updated() > EXPOSURE_STALE_TICKS);
        return List.copyOf(exposures.values());
    }

    /** Drops every transform and exposure, as a disconnect does. */
    public void clear() {
        playing.clear();
        exposures.clear();
    }
}
