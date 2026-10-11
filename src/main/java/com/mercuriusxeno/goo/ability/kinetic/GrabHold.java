package com.mercuriusxeno.goo.ability.kinetic;

/**
 * What a player's Grab holds: the held entity, the speed a left click
 * throws it at, and the last game time the hold stands without another
 * channel tick. Held server side only, for the length of a hold.
 * grab-holds-and-throws-a-physics-body
 *
 * @param entityId   the held entity's id, NO_ENTITY while the player holds nothing
 * @param throwSpeed the speed a throw launches the entity at, in blocks per tick
 * @param heldUntil  the last game time the hold stands
 */
public record GrabHold(int entityId, double throwSpeed, long heldUntil) {

    /** The entity id of a hold holding nothing. */
    public static final int NO_ENTITY = -1;

    /** A player holding nothing. */
    public static final GrabHold NONE = new GrabHold(NO_ENTITY, 0, 0L);

    /**
     * Answers whether the hold still stands at a game time.
     *
     * @param now the game time
     * @return true while the hold holds an entity and has not run out
     */
    public boolean standsAt(long now) {
        return entityId != NO_ENTITY && now <= heldUntil;
    }
}
