package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.data.GooFraction;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * How long each player has held a stream, counted in server ticks: a
 * stream tick arriving within two ticks of the last continues the hold, a
 * second one in the same server tick runs nothing, and a longer gap starts a
 * new one (decision stream-delivery-held-cone). Each hold also keeps the
 * marks it left on the blocks it reached, so a stream painting blocks keeps
 * them transitioning and steps each position at most once per hold
 * (decision decay-gnats-degrade-each-block-once).
 */
public final class StreamHolds {

    /**
     * The most server ticks a stream tick may land after the last and still
     * continue the hold: a client's stream ticks jitter against the
     * server's, so one lands a tick late now and then.
     */
    private static final int LATEST_CONTINUING_GAP = 2;

    private final Map<UUID, Hold> holds = new HashMap<>();
    /** The players whose current hold has paid the reagents its ability consumes. */
    private final Set<UUID> paidHolds = new HashSet<>();

    /**
     * Counts one stream tick for the player; a new hold starts with fresh marks.
     *
     * @param player the streaming player
     * @param tick   the server tick the stream tick arrived on
     * @return the hold's tick count, 1 on the hold's first tick, and 0 for a second
     *         stream tick in one server tick, which runs nothing
     */
    public int advance(UUID player, int tick) {
        Hold last = holds.get(player);
        if (last != null && last.tick() == tick) {
            return 0;
        }
        boolean continues = last != null && tick - last.tick() <= LATEST_CONTINUING_GAP;
        Hold next = continues ? new Hold(tick, last.held() + 1, last.marks())
                : new Hold(tick, 1, new HoldMarks());
        if (!continues) {
            paidHolds.remove(player);
            if (last != null) {
                last.marks().gradualBreak().release();
            }
        }
        holds.put(player, next);
        return next.held();
    }

    /**
     * The marks the player's current hold has left.
     *
     * @param player the streaming player
     * @return the hold's marks, fresh ones for a player holding nothing
     */
    public HoldMarks marks(UUID player) {
        Hold hold = holds.get(player);
        return hold != null ? hold.marks() : new HoldMarks();
    }

    /**
     * Records that the player's current hold paid its reagents, once on its
     * first tick (decision ability-json-names-its-reagent).
     *
     * @param player the holding player
     */
    public void markPaid(UUID player) {
        paidHolds.add(player);
    }

    /**
     * Whether the player's current hold paid its reagents.
     *
     * @param player the holding player
     * @return true once the hold's first tick took them
     */
    public boolean paid(UUID player) {
        return paidHolds.contains(player);
    }

    /**
     * Clears the cracks of every hold that has gone past its gap without a
     * stream tick, which is a hold its player let go.
     * decision gradual-breaks-share-one-break-texture-piece
     *
     * @param tick the server tick now
     */
    public void releaseLapsed(int tick) {
        for (Hold hold : holds.values()) {
            if (tick - hold.tick() > LATEST_CONTINUING_GAP) {
                hold.marks().gradualBreak().release();
            }
        }
    }

    /** Drops every hold, as a server stop does. */
    public void clear() {
        holds.clear();
        paidHolds.clear();
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
        return shareAt(GooFraction.of(cost), ticksPerCharge, held);
    }

    /**
     * The whole mB one tick of a hold drains of an exact cost, so every run of ticks drains the cost's whole part of
     * the ticks held so far: 12 1/2 over twenty ticks drains 12 across the first charge and 13 across the second.
     * decision goo-is-an-int-and-only-the-remainder-is-a-fraction
     *
     * @param cost           the ability's exact cost, in mB
     * @param ticksPerCharge the ticks of hold one cost pays for
     * @param held           the hold's tick count, 1 on its first tick
     * @return the mB this tick drains
     */
    public static int shareAt(GooFraction cost, int ticksPerCharge, int held) {
        GooFraction perTick = cost.dividedBy(ticksPerCharge);
        return perTick.times(held).floor() - perTick.times(held - 1L).floor();
    }

    private record Hold(int tick, int held, HoldMarks marks) {
    }
}
