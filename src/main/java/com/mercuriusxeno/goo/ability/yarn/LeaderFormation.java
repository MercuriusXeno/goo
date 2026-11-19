package com.mercuriusxeno.goo.ability.yarn;

import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/**
 * Each leader's formation as the server sees it this tick: its minions in
 * lead order and the slot each takes, read once a tick however many of its
 * minions ask. Every loaded minion standing in the lead counts, fighting or
 * not, so a fight leaves the others' slots where they were; a released, dead
 * or downed minion leaves the formation and every later one moves up.
 * each-rallied-mob-locks-into-a-fixed-slot
 * slots-follow-rally-order-size-and-count
 */
final class LeaderFormation {

    /** Each leader's formation as last read, held weakly so a leader gone from the world drops out. */
    private static final Map<Entity, Lineup> LINEUPS = new WeakHashMap<>();

    private LeaderFormation() {
    }

    /**
     * The slot a minion takes in its leader's formation this tick.
     *
     * @param leader  the leader
     * @param minion   the led minion
     * @param minions  every loaded mob the leader led, read only when this tick's formation is unread
     * @param gameTime the level's game time this tick
     * @return the slot, or empty where the minion stands outside the formation
     */
    static Optional<LeadFollow.Slot> slotOf(Entity leader, Mob minion, Supplier<List<Mob>> minions, long gameTime) {
        Lineup lineup = LINEUPS.get(leader);
        // a minion led after this tick's read is missing from it, so the read runs again
        if (lineup == null || lineup.gameTime() != gameTime || !lineup.minions().contains(minion)) {
            lineup = lineUp(leader.getBbWidth(), minions.get(), gameTime);
            LINEUPS.put(leader, lineup);
        }
        int index = lineup.minions().indexOf(minion);
        return index < 0 ? Optional.empty() : Optional.of(lineup.slots().get(index));
    }

    private static Lineup lineUp(double leaderWidth, List<Mob> led, long gameTime) {
        List<Mob> standing = led.stream()
                .filter(mob -> mob.isAlive() && !mob.hasData(GooAttachments.DOWNED)).toList();
        List<Mob> ordered = LeadFollow.inLeadOrder(standing,
                mob -> mob.getData(GooAttachments.LED).leadOrder(), Entity::getUUID);
        List<Double> widths = ordered.stream().map(mob -> (double) mob.getBbWidth()).toList();
        return new Lineup(ordered, LeadFollow.formation(leaderWidth, widths), gameTime);
    }

    /**
     * A leader's minions in lead order, the slot each takes, and the tick they were read.
     *
     * @param minions  the minions in lead order
     * @param slots    the slot of each, index for index
     * @param gameTime the level's game time of the read
     */
    private record Lineup(List<Mob> minions, List<LeadFollow.Slot> slots, long gameTime) {
    }
}
