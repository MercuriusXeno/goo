package com.mercuriusxeno.goo.ability.zoo;

import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/**
 * Each rallier's formation as the server sees it this tick: its minions in
 * rally order and the slot each takes, read once a tick however many of its
 * minions ask. Every loaded minion standing in the rally counts, fighting or
 * not, so a fight leaves the others' slots where they were; a released, dead
 * or downed minion leaves the formation and every later one moves up.
 * each-rallied-mob-locks-into-a-fixed-slot
 * slots-follow-rally-order-size-and-count
 */
final class RallierFormation {

    /** Each rallier's formation as last read, held weakly so a rallier gone from the world drops out. */
    private static final Map<Entity, Lineup> LINEUPS = new WeakHashMap<>();

    private RallierFormation() {
    }

    /**
     * The slot a minion takes in its rallier's formation this tick.
     *
     * @param rallier  the rallier
     * @param minion   the rallied minion
     * @param minions  every loaded mob the rallier rallied, read only when this tick's formation is unread
     * @param gameTime the level's game time this tick
     * @return the slot, or empty where the minion stands outside the formation
     */
    static Optional<RallyFollow.Slot> slotOf(Entity rallier, Mob minion, Supplier<List<Mob>> minions, long gameTime) {
        Lineup lineup = LINEUPS.get(rallier);
        // a minion rallied after this tick's read is missing from it, so the read runs again
        if (lineup == null || lineup.gameTime() != gameTime || !lineup.minions().contains(minion)) {
            lineup = lineUp(minions.get(), gameTime);
            LINEUPS.put(rallier, lineup);
        }
        int index = lineup.minions().indexOf(minion);
        return index < 0 ? Optional.empty() : Optional.of(lineup.slots().get(index));
    }

    private static Lineup lineUp(List<Mob> rallied, long gameTime) {
        List<Mob> standing = rallied.stream()
                .filter(mob -> mob.isAlive() && !mob.hasData(GooAttachments.DOWNED)).toList();
        List<Mob> ordered = RallyFollow.inRallyOrder(standing,
                mob -> mob.getData(GooAttachments.RALLIED).rallyOrder(), Entity::getUUID);
        List<Double> widths = ordered.stream().map(mob -> (double) mob.getBbWidth()).toList();
        return new Lineup(ordered, RallyFollow.formation(widths), gameTime);
    }

    /**
     * A rallier's minions in rally order, the slot each takes, and the tick they were read.
     *
     * @param minions  the minions in rally order
     * @param slots    the slot of each, index for index
     * @param gameTime the level's game time of the read
     */
    private record Lineup(List<Mob> minions, List<RallyFollow.Slot> slots, long gameTime) {
    }
}
