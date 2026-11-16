package com.mercuriusxeno.goo.ability.kinetic;

/**
 * The item entity a player's Grab press locked: it homes on the player each
 * channel tick wherever the look goes, until a few ticks after the last
 * channel tick. Held server side only, for the length of the press.
 * grabbed-item-locks-in-and-homes-on-your-current-position
 *
 * @param itemId      the locked item entity's id, NO_ITEM while the press locks nothing
 * @param lockedUntil the last game time the lock stands without another channel tick
 */
public record GrabItemLock(int itemId, long lockedUntil) {

    /** The item id of a lock locking nothing. */
    public static final int NO_ITEM = -1;

    /** A player whose press locks no item. */
    public static final GrabItemLock NONE = new GrabItemLock(NO_ITEM, 0L);

    /**
     * Answers whether the lock still stands at a game time.
     *
     * @param now the game time
     * @return true while the lock names an item and has not run out
     */
    public boolean standsAt(long now) {
        return itemId != NO_ITEM && now <= lockedUntil;
    }
}
