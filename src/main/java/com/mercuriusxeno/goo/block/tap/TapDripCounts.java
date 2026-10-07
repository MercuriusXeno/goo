package com.mercuriusxeno.goo.block.tap;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import java.util.HashMap;
import java.util.Map;

/**
 * How many tap drips each block has taken since its tap ability last acted,
 * so an ability can act once a number of drips has accumulated, as petrify's
 * tap does (decision petrify-drip-calcifies-and-grows-dripstone). The counts
 * live with the running server and start over when it stops.
 */
public final class TapDripCounts {

    private final Map<Landing, Integer> counts = new HashMap<>();

    /**
     * A block a drip lands on, in its dimension.
     *
     * @param dimension the level's dimension
     * @param pos       the block
     */
    private record Landing(ResourceKey<Level> dimension, BlockPos pos) {
    }

    /**
     * Counts one more drip on a block.
     *
     * @param dimension the level's dimension
     * @param pos       the block the drip landed on
     * @return the drips the block has taken, this one included
     */
    public int countDrip(ResourceKey<Level> dimension, BlockPos pos) {
        return counts.merge(new Landing(dimension, pos.immutable()), 1, Integer::sum);
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
