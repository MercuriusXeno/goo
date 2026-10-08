package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.program.UnmakeLoot;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * How long each player has held a stream, counted in server ticks: a
 * stream tick arriving the tick after the last one continues the hold, and
 * any gap starts a new one (decision stream-delivery-held-cone).
 */
public final class StreamHolds {

    /** Server ticks a held block or mob may go unworked before its hold starts over. */
    static final int HOLD_GRACE_TICKS = 5;

    private final Map<UUID, Hold> holds = new HashMap<>();
    private final Map<UUID, Map<BlockPos, Hold>> blockHolds = new HashMap<>();
    private final Map<UUID, Map<UUID, Hold>> mobHolds = new HashMap<>();
    private final Map<UUID, Map<UUID, Optional<UnmakeLoot.Loot>>> mobLoot = new HashMap<>();

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
        return advanceIn(blockHolds.computeIfAbsent(player, ignored -> new HashMap<>()), pos.immutable(), tick);
    }

    /**
     * Counts one stream tick on a mob the player's stream holds, as a block's
     * hold counts; a mob the stream left forgets its rolled loot with its hold
     * (decision unmake-waves-dissolve-by-crucible-cost).
     *
     * @param player the streaming player
     * @param mob    the held mob's id
     * @param tick   the server tick the stream tick arrived on
     * @return the mob's hold tick count, 1 on the hold's first tick
     */
    public int advanceMob(UUID player, UUID mob, int tick) {
        Map<UUID, Hold> held = mobHolds.computeIfAbsent(player, ignored -> new HashMap<>());
        int count = advanceIn(held, mob, tick);
        mobLoot.computeIfAbsent(player, ignored -> new HashMap<>()).keySet().retainAll(held.keySet());
        return count;
    }

    /**
     * A held mob's loot, rolled the first time the hold asks and kept for the
     * rest of the hold, so the work it takes holds still.
     *
     * @param player the streaming player
     * @param mob    the held mob's id
     * @param roll   rolls the mob's loot, null when it drops nothing of value
     * @return the loot, or null when it drops nothing of value
     */
    public UnmakeLoot.@Nullable Loot lootOf(UUID player, UUID mob, Supplier<UnmakeLoot.@Nullable Loot> roll) {
        return mobLoot.computeIfAbsent(player, ignored -> new HashMap<>())
                .computeIfAbsent(mob, ignored -> Optional.ofNullable(roll.get())).orElse(null);
    }

    /**
     * Counts one tick on a held thing: one held within the last few ticks
     * continues its hold, any other starts anew, and one left longer is
     * forgotten. The grace carries a hold over the server ticks a batch of
     * stream ticks leaves empty, two arriving in one tick and none the next.
     *
     * @param held the holds of one player's stream
     * @param key  the held thing
     * @param tick the server tick
     * @param <K>  what the holds are keyed by
     * @return the thing's hold tick count, 1 on the hold's first tick
     */
    private static <K> int advanceIn(Map<K, Hold> held, K key, int tick) {
        held.values().removeIf(hold -> hold.tick() < tick - HOLD_GRACE_TICKS);
        Hold last = held.get(key);
        int count = last == null ? 1 : last.tick() == tick ? last.held() : last.held() + 1;
        held.put(key, new Hold(tick, count));
        return count;
    }

    /** Drops every hold, as a server stop does. */
    public void clear() {
        holds.clear();
        blockHolds.clear();
        mobHolds.clear();
        mobLoot.clear();
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
