package com.mercuriusxeno.goo.ability.kinetic;

import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The item entities a player's Grab press locked: each homes on the player
 * every channel tick wherever the look goes, until a few ticks after the last
 * channel tick. The lock stands as long as the press, the items it already
 * delivered included, so the press takes no mob until right click lets go.
 * A group locked together first meets on the aim line, each group with its
 * own meet. Held server side only, for the length of the press.
 * grabbed-item-locks-in-and-homes-on-your-current-position
 * sweeping-grab-locks-every-item-the-highlight-passes
 * grabbed-item-group-converges-on-the-aim-line-then-pulls-as-one
 *
 * @param itemIds     the ids of the item entities the press locked, in the order it locked them
 * @param lockedUntil the last game time the lock stands without another channel tick
 * @param meets       the meets of the groups still flying to their meet point
 */
public record GrabItemLock(List<Integer> itemIds, long lockedUntil, List<Meet> meets) {

    /** A player whose press locks no item. */
    public static final GrabItemLock NONE = new GrabItemLock(List.of(), 0L, List.of());

    /**
     * The lock with its own lists copied, so no caller's list changes it.
     *
     * @param itemIds     the locked item ids
     * @param lockedUntil the last game time the lock stands
     * @param meets       the meets still flying
     */
    public GrabItemLock {
        itemIds = List.copyOf(itemIds);
        meets = List.copyOf(meets);
    }

    /**
     * One locked group's meet: each member flies straight to the meet point
     * at its own speed, so all land there on one tick, and the group combines
     * at the meet's end.
     * grabbed-item-group-converges-on-the-aim-line-then-pulls-as-one
     *
     * @param speeds the speed each member flies at, in blocks per tick, by entity id
     * @param point  the meet point on the aim line
     * @param endsAt the game time the last member lands, fractional
     */
    public record Meet(Map<Integer, Double> speeds, Vec3 point, double endsAt) {

        /**
         * The meet with its own speeds copied.
         *
         * @param speeds the speeds by entity id
         * @param point  the meet point
         * @param endsAt the game time the meet ends
         */
        public Meet {
            speeds = Map.copyOf(speeds);
        }
    }

    /**
     * Answers whether the lock still stands at a game time.
     *
     * @param now the game time
     * @return true while the press has locked an item and the lock has not run out
     */
    public boolean standsAt(long now) {
        return !itemIds.isEmpty() && now <= lockedUntil;
    }

    /**
     * The lock after one channel tick: the items and meets it held while it
     * stood, and the items this tick locks with their meet, stretched to a
     * later end; a lock that ran out holds only this tick's, so a new press
     * starts afresh.
     * sweeping-grab-locks-every-item-the-highlight-passes
     *
     * @param adding the ids of the items this tick locks
     * @param meet   the meet of the group this tick locks, empty for a lone item or none
     * @param now    the game time
     * @param until  the last game time the lock stands without another channel tick
     * @return the lock this tick leaves
     */
    public GrabItemLock stretched(List<Integer> adding, Optional<Meet> meet, long now, long until) {
        boolean stands = standsAt(now);
        Set<Integer> ids = new LinkedHashSet<>(stands ? itemIds : List.of());
        ids.addAll(adding);
        List<Meet> flying = new ArrayList<>(stands ? meets : List.of());
        meet.ifPresent(flying::add);
        return new GrabItemLock(List.copyOf(ids), until, flying);
    }

    /**
     * The meet a locked item still flies to.
     *
     * @param itemId the item entity's id
     * @return its group's meet, empty once the group met or for an item locked alone
     */
    public Optional<Meet> meetOf(int itemId) {
        return meets.stream().filter(meet -> meet.speeds().containsKey(itemId)).findFirst();
    }

    /**
     * The lock with a finished meet dropped, so its group homes as one.
     *
     * @param met the meet that ended
     * @return the lock without it
     */
    public GrabItemLock without(Meet met) {
        return new GrabItemLock(itemIds, lockedUntil, meets.stream().filter(meet -> !meet.equals(met)).toList());
    }
}
