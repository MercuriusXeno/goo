package com.mercuriusxeno.goo.ability.kinetic;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The item entities a player's Grab press locked: each homes on the player
 * every channel tick wherever the look goes, until a few ticks after the last
 * channel tick. The lock stands as long as the press, the items it already
 * delivered included, so the press takes no mob until right click lets go.
 * Held server side only, for the length of the press.
 * grabbed-item-locks-in-and-homes-on-your-current-position
 * sweeping-grab-locks-every-item-the-highlight-passes
 *
 * @param itemIds     the ids of the item entities the press locked, in the order it locked them
 * @param lockedUntil the last game time the lock stands without another channel tick
 */
public record GrabItemLock(List<Integer> itemIds, long lockedUntil) {

    /** A player whose press locks no item. */
    public static final GrabItemLock NONE = new GrabItemLock(List.of(), 0L);

    /**
     * The lock with its own items copied, so no caller's list changes it.
     *
     * @param itemIds     the locked item ids
     * @param lockedUntil the last game time the lock stands
     */
    public GrabItemLock {
        itemIds = List.copyOf(itemIds);
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
     * The lock after one channel tick: the items it held while it stood, and
     * the items this tick locks, stretched to a later end; a lock that ran
     * out holds only this tick's items, so a new press starts afresh.
     * sweeping-grab-locks-every-item-the-highlight-passes
     *
     * @param adding the ids of the items this tick locks
     * @param now    the game time
     * @param until  the last game time the lock stands without another channel tick
     * @return the lock this tick leaves
     */
    public GrabItemLock stretched(List<Integer> adding, long now, long until) {
        Set<Integer> ids = new LinkedHashSet<>(standsAt(now) ? itemIds : List.of());
        ids.addAll(adding);
        return new GrabItemLock(List.copyOf(ids), until);
    }
}
