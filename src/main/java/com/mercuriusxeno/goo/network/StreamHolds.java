package com.mercuriusxeno.goo.network;

import net.minecraft.core.BlockPos;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * How long each player has held a stream, counted in server ticks: a
 * stream tick arriving the tick after the last one continues the hold, and
 * any gap starts a new one (decision stream-delivery-held-cone). Each hold
 * also keeps the block positions it has stepped, so a stream stepping
 * blocks steps each position at most once per hold
 * (decision decay-gnats-degrade-each-block-once).
 */
public final class StreamHolds {

    private final Map<UUID, Hold> holds = new HashMap<>();

    /**
     * Counts one stream tick for the player; a new hold forgets the
     * positions the last one stepped.
     *
     * @param player the streaming player
     * @param tick   the server tick the stream tick arrived on
     * @return the hold's tick count, 1 on the hold's first tick
     */
    public int advance(UUID player, int tick) {
        Hold last = holds.get(player);
        boolean continues = last != null && last.tick() == tick - 1;
        Hold next = continues ? new Hold(tick, last.held() + 1, last.stepped()) : new Hold(tick, 1, new HashSet<>());
        holds.put(player, next);
        return next.held();
    }

    /**
     * Whether the player's current hold has stepped a block position.
     *
     * @param player the streaming player
     * @param pos    the block position
     * @return true once {@link #noteStepped} named the position in this hold
     */
    public boolean stepped(UUID player, BlockPos pos) {
        Hold hold = holds.get(player);
        return hold != null && hold.stepped().contains(pos);
    }

    /**
     * Notes that the player's current hold stepped a block position.
     *
     * @param player the streaming player
     * @param pos    the block position
     */
    public void noteStepped(UUID player, BlockPos pos) {
        Hold hold = holds.get(player);
        if (hold != null) {
            hold.stepped().add(pos.immutable());
        }
    }

    /** Drops every hold, as a server stop does. */
    public void clear() {
        holds.clear();
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

    private record Hold(int tick, int held, Set<BlockPos> stepped) {
    }
}
