package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import java.util.HashMap;
import java.util.Map;

/**
 * The drips an unmaking tap has landed on each block: a drip on the block
 * the last one struck counts on, and a drip on a block changed since starts
 * over, so the tap dissolves the block below once its drips reach the
 * block's crucible cost.
 * decision unmake-drip-dissolves-the-block-below
 */
public final class TapUnmakeDrips {

    private record Landing(ResourceKey<Level> dimension, BlockPos pos) {
    }

    private record Tally(BlockState state, int drips) {
    }

    private final Map<Landing, Tally> tallies = new HashMap<>();

    /**
     * Counts one drip landing on a block.
     *
     * @param dimension the level the block stands in
     * @param pos       the block
     * @param state     the block's state as the drip lands
     * @return the drips landed on the block as it stands, 1 on the first
     */
    public int count(ResourceKey<Level> dimension, BlockPos pos, BlockState state) {
        Landing landing = new Landing(dimension, pos.immutable());
        Tally last = tallies.get(landing);
        int drips = last != null && last.state() == state ? last.drips() + 1 : 1;
        tallies.put(landing, new Tally(state, drips));
        return drips;
    }

    /**
     * Forgets a block's drips, as its unmaking does.
     *
     * @param dimension the level the block stood in
     * @param pos       the block
     */
    public void forget(ResourceKey<Level> dimension, BlockPos pos) {
        tallies.remove(new Landing(dimension, pos));
    }

    /** Drops every tally, as a server stop does. */
    public void clear() {
        tallies.clear();
    }
}
