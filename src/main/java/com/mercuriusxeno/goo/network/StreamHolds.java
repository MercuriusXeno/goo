package com.mercuriusxeno.goo.network;

import net.minecraft.core.BlockPos;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * How long each player has held a stream, counted in server ticks: a
 * stream tick arriving the tick after the last one continues the hold, and
 * any gap starts a new one (decision stream-delivery-held-cone).
 */
public final class StreamHolds {

    private final Map<UUID, Hold> holds = new HashMap<>();
    private final Map<UUID, Map<BlockPos, Hold>> blockHolds = new HashMap<>();

    /**
     * Counts one stream tick for the player.
     *
     * @param player the streaming player
     * @param tick   the server tick the stream tick arrived on
     * @return the hold's tick count, 1 on the hold's first tick
     */
    public int advance(UUID player, int tick) {
        Hold last = holds.get(player);
        int held = last != null && last.tick() == tick - 1 ? last.held() + 1 : 1;
        holds.put(player, new Hold(tick, held));
        return held;
    }

    /**
     * Counts one stream tick on a block the player's stream holds: a block
     * held the tick before continues its hold, any other starts anew, and a
     * block the stream left before the last tick is forgotten
     * (decision unmake-waves-dissolve-by-crucible-cost).
     *
     * @param player the streaming player
     * @param pos    the held block
     * @param tick   the server tick the stream tick arrived on
     * @return the block's hold tick count, 1 on the hold's first tick
     */
    public int advanceBlock(UUID player, BlockPos pos, int tick) {
        Map<BlockPos, Hold> held = blockHolds.computeIfAbsent(player, ignored -> new HashMap<>());
        held.values().removeIf(hold -> hold.tick() < tick - 1);
        Hold last = held.get(pos);
        int count = last == null ? 1 : last.tick() == tick ? last.held() : last.held() + 1;
        held.put(pos.immutable(), new Hold(tick, count));
        return count;
    }

    /** Drops every hold, as a server stop does. */
    public void clear() {
        holds.clear();
        blockHolds.clear();
    }

    /**
     * The goo one tick of a hold drains: the cost spread across the ticks one
     * charge lasts, rounded so every run of those ticks drains the cost exactly.
     *
     * @param cost           the ability's cost at stack zero, in mB
     * @param ticksPerCharge the ticks of hold one cost pays for
     * @param held           the hold's tick count, 1 on its first tick
     * @return the mB this tick drains
     */
    public static int shareAt(int cost, int ticksPerCharge, int held) {
        long total = (long) cost * held / ticksPerCharge;
        long before = (long) cost * (held - 1) / ticksPerCharge;
        return (int) (total - before);
    }

    private record Hold(int tick, int held) {
    }
}
