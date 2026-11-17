package com.mercuriusxeno.goo.ability.kinetic;

import net.minecraft.world.phys.Vec3;

/**
 * What a player's Grab holds: the held entity, the speed a left click
 * throws it at, and the last game time the hold stands without another
 * channel tick; or, after a throw, a spent press that lifts nothing until
 * the channel lets go. Held server side only, for the length of a hold.
 * grab-holds-and-throws-a-physics-body
 *
 * @param entityId   the held entity's id, NO_ENTITY while the player holds nothing
 * @param throwSpeed the speed a throw launches the entity at, in blocks per tick
 * @param heldUntil  the last game time the hold, or the spent press, stands
 * @param spent      whether a throw spent this press, so it lifts nothing more
 * @param velocity   how far the held entity moved on the hold's last tick, the speed it carries into the next
 */
public record GrabHold(int entityId, double throwSpeed, long heldUntil, boolean spent, Vec3 velocity) {

    /** The entity id of a hold holding nothing. */
    public static final int NO_ENTITY = -1;

    /** A player holding nothing. */
    public static final GrabHold NONE = new GrabHold(NO_ENTITY, 0, 0L, false, Vec3.ZERO);

    /**
     * A press a throw spent, standing until a game time.
     * decision diagnose-and-correct-grabs-left-click-throw
     *
     * @param until the last game time the spent press stands without another channel tick
     * @return the spent press
     */
    public static GrabHold spentUntil(long until) {
        return new GrabHold(NO_ENTITY, 0, until, true, Vec3.ZERO);
    }

    /**
     * Answers whether the hold still stands at a game time.
     *
     * @param now the game time
     * @return true while the hold holds an entity and has not run out
     */
    public boolean standsAt(long now) {
        return entityId != NO_ENTITY && now <= heldUntil;
    }

    /**
     * Answers whether a throw spent this press and the channel has not let go since.
     *
     * @param now the game time
     * @return true while the spent press stands
     */
    public boolean spentAt(long now) {
        return spent && now <= heldUntil;
    }
}
