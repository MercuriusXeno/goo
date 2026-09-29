package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.registry.GooServerState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Prevents rapid re-triggering of canister placement and pickup
 * when the player holds the interact button across multiple ticks.
 * Each server holds one, so its marks end with the server
 * (decision type-package-and-per-server-holders).
 */
public final class InteractionCooldown {

    /** Minimum ticks between consecutive canister place/pickup interactions. */
    private static final long COOLDOWN_TICKS = 10;

    /** Tracks the last successful interaction tick per player. */
    private final Map<UUID, Long> lastInteraction = new HashMap<>();

    /**
     * Answers whether the player is still within the cooldown on the level's
     * server; a client level holds no marks, so it answers false.
     *
     * @param level  the level the player interacts in
     * @param player the interacting player
     * @return true if the cooldown has not yet elapsed
     */
    public static boolean isOnCooldown(Level level, Player player) {
        GooServerState state = GooServerState.of(level);
        return state != null && state.interactionCooldown().isOnCooldown(player.getUUID(), level.getGameTime());
    }

    /**
     * Marks a successful interaction on the level's server; a client level holds no marks.
     *
     * @param level  the level the player interacts in
     * @param player the interacting player
     */
    public static void markInteraction(Level level, Player player) {
        GooServerState state = GooServerState.of(level);
        if (state != null) {
            state.interactionCooldown().markInteraction(player.getUUID(), level.getGameTime());
        }
    }

    /**
     * Returns true if the player is still within the cooldown window.
     *
     * @param playerId the player's UUID
     * @param gameTick the current game tick from Level.getGameTime()
     * @return true if the cooldown has not yet elapsed
     */
    public boolean isOnCooldown(UUID playerId, long gameTick) {
        Long last = lastInteraction.get(playerId);
        return last != null && gameTick - last < COOLDOWN_TICKS;
    }

    /**
     * Records a successful interaction for cooldown tracking.
     *
     * @param playerId the player's UUID
     * @param gameTick the current game tick from Level.getGameTime()
     */
    public void markInteraction(UUID playerId, long gameTick) {
        lastInteraction.put(playerId, gameTick);
    }

    /**
     * Answers whether no mark stands.
     *
     * @return true when no player holds a mark
     */
    public boolean isEmpty() {
        return lastInteraction.isEmpty();
    }

    /**
     * Drops every mark, as a server stop does.
     */
    public void clear() {
        lastInteraction.clear();
    }
}
