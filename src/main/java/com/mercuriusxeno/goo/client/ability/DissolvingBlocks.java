package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import java.util.HashMap;
import java.util.Map;

/**
 * The blocks an unmake is dissolving on this client, each with the share
 * last heard and the game time it arrived; a block the unmake stopped
 * working, unheard for {@link #STALE_TICKS}, is forgotten.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public final class DissolvingBlocks {

    /** The store the client's unmake handler and the dissolve renderer share. */
    public static final DissolvingBlocks CLIENT = new DissolvingBlocks();

    /** Ticks after the last share before a block is forgotten. */
    static final long STALE_TICKS = 3;

    private record Heard(float fraction, long tick) {
    }

    private final Map<BlockPos, Heard> dissolving = new HashMap<>();

    /**
     * Records the share a block has dissolved.
     *
     * @param pos      the block
     * @param fraction the share dissolved, from 0 whole to 1 gone
     * @param now      the game time the share arrived
     */
    public void record(BlockPos pos, float fraction, long now) {
        dissolving.put(pos.immutable(), new Heard(fraction, now));
    }

    /**
     * The blocks still dissolving and the share of each, forgetting each
     * gone quiet.
     *
     * @param now the game time
     * @return each dissolving block's share, from 0 whole to 1 gone
     */
    public Map<BlockPos, Float> live(long now) {
        dissolving.values().removeIf(heard -> now - heard.tick() > STALE_TICKS);
        Map<BlockPos, Float> shares = new HashMap<>();
        dissolving.forEach((pos, heard) -> shares.put(pos, heard.fraction()));
        return shares;
    }
}
