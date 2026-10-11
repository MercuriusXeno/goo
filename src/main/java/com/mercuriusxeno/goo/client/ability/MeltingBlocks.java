package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The share each block an unmake is working has melted on this client, as
 * last heard; a block unheard for {@link #STALE_TICKS} reads as unworked.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public final class MeltingBlocks {

    /** The store the client's unmake handler and the melt renderers share. */
    public static final MeltingBlocks CLIENT = new MeltingBlocks();

    /** Ticks after the last share before a block reads as unworked. */
    static final long STALE_TICKS = 3;

    private record Heard(float melted, long tick) {
    }

    private final Map<BlockPos, Heard> melting = new HashMap<>();

    /**
     * Records the share of a block melted.
     *
     * @param pos      the block
     * @param fraction the share melted, from 0 whole to 1 gone
     * @param now      the game time the share arrived
     */
    public void record(BlockPos pos, float fraction, long now) {
        melting.put(pos.immutable(), new Heard(fraction, now));
    }

    /**
     * The share a block has melted, while the unmake still works it.
     *
     * @param pos the block
     * @param now the game time including the partial tick
     * @return the share, empty once the block is unheard long enough
     */
    public Optional<Float> meltedAt(BlockPos pos, float now) {
        Heard heard = melting.get(pos);
        if (heard == null || now - heard.tick() > STALE_TICKS) {
            melting.remove(pos);
            return Optional.empty();
        }
        return Optional.of(heard.melted());
    }

    /**
     * Every block still being worked and its share, forgetting each gone quiet.
     *
     * @param now the game time including the partial tick
     * @return each worked block's share
     */
    public Map<BlockPos, Float> worked(float now) {
        melting.values().removeIf(heard -> now - heard.tick() > STALE_TICKS);
        Map<BlockPos, Float> shares = new HashMap<>();
        melting.forEach((pos, heard) -> shares.put(pos, heard.melted()));
        return shares;
    }
}
