package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The blocks an unmake is melting on this client. A standing block's surface
 * liquefies as the share the server sends rises; once the block is gone its
 * goo collapses for {@link #COLLAPSE_TICKS} and is forgotten. A standing
 * block the unmake stopped working, unheard for {@link #STALE_TICKS}, is
 * forgotten as it stands.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public final class MeltingBlocks {

    /** The store the client's unmake handler and the melt renderer share. */
    public static final MeltingBlocks CLIENT = new MeltingBlocks();

    /** Ticks after the last share before a standing block is forgotten. */
    static final long STALE_TICKS = 3;
    /** Ticks a gone block's goo takes to collapse, half a second. */
    static final long COLLAPSE_TICKS = 10;
    private static final long STANDING = -1;

    /**
     * One block's melt as this frame draws it.
     *
     * @param pos        the block
     * @param liquefied  how much of its surface has turned to goo, 0 to 1
     * @param collapse   how far its goo has collapsed once the block is gone, 0 while it stands, to 1
     */
    public record Melt(BlockPos pos, float liquefied, float collapse) {
    }

    private record Heard(float liquefied, long tick, long goneAt) {
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
        melting.put(pos.immutable(), new Heard(fraction, now, STANDING));
    }

    /**
     * The melts to draw now: each standing block still being worked, and each
     * gone block's collapsing goo, forgetting those finished or gone quiet.
     *
     * @param now    the game time including the partial tick
     * @param isGone whether a block has gone from the level
     * @return the melts
     */
    public List<Melt> melts(float now, Predicate<BlockPos> isGone) {
        List<Melt> melts = new ArrayList<>();
        Iterator<Map.Entry<BlockPos, Heard>> entries = melting.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<BlockPos, Heard> entry = entries.next();
            Heard heard = entry.getValue();
            if (heard.goneAt() == STANDING && isGone.test(entry.getKey())) {
                heard = new Heard(1f, heard.tick(), (long) now);
                entry.setValue(heard);
            }
            Melt melt = meltOf(entry.getKey(), heard, now);
            if (melt == null) {
                entries.remove();
            } else {
                melts.add(melt);
            }
        }
        return melts;
    }

    /**
     * One block's melt now: its liquefying share while it stands, its goo's
     * collapse once it is gone.
     *
     * @param pos   the block
     * @param heard what was last heard of it
     * @param now   the game time including the partial tick
     * @return the melt, or null once it has collapsed or gone quiet
     */
    private static @Nullable Melt meltOf(BlockPos pos, Heard heard, float now) {
        if (heard.goneAt() == STANDING) {
            return now - heard.tick() > STALE_TICKS ? null : new Melt(pos, heard.liquefied(), 0f);
        }
        float collapse = (now - heard.goneAt()) / COLLAPSE_TICKS;
        return collapse >= 1f ? null : new Melt(pos, 1f, Math.max(collapse, 0f));
    }
}
