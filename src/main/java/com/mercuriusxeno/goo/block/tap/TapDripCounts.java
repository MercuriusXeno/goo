package com.mercuriusxeno.goo.block.tap;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import java.util.HashMap;
import java.util.Map;

/**
 * How many tap drips each block has taken since its tap ability last acted,
 * so an ability can act once a number of drips has accumulated, as petrify's
 * and unmake's taps do (decisions petrify-drip-calcifies-and-grows-dripstone,
 * unmake-drip-dissolves-the-block-below). A block swapped for another since
 * its last drip starts its count over. The counts live with the running
 * server and start over when it stops.
 */
public final class TapDripCounts {

    private final Map<Landing, Tally> counts = new HashMap<>();

    /**
     * A block a drip lands on, in its dimension.
     *
     * @param dimension the level's dimension
     * @param pos       the block
     */
    private record Landing(ResourceKey<Level> dimension, BlockPos pos) {
    }

    /**
     * A block's count and the state it stood in when last counted.
     *
     * @param state the block's state at its last drip
     * @param drips the drips it has taken
     */
    private record Tally(BlockState state, int drips) {
    }

    /**
     * Counts one more drip on a block, starting over when the block changed
     * since its last drip.
     *
     * @param dimension the level's dimension
     * @param pos       the block the drip landed on
     * @param state     the block's state as the drip lands
     * @return the drips the block has taken as it stands, this one included
     */
    public int countDrip(ResourceKey<Level> dimension, BlockPos pos, BlockState state) {
        Landing landing = new Landing(dimension, pos.immutable());
        Tally last = counts.get(landing);
        int drips = last != null && last.state() == state ? last.drips() + 1 : 1;
        counts.put(landing, new Tally(state, drips));
        return drips;
    }

    /**
     * Starts a block's count over.
     *
     * @param dimension the level's dimension
     * @param pos       the block
     */
    public void reset(ResourceKey<Level> dimension, BlockPos pos) {
        counts.remove(new Landing(dimension, pos));
    }

    /** Drops every count, as a server stop does. */
    public void clear() {
        counts.clear();
    }
}
