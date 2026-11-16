package com.mercuriusxeno.goo.block.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import java.util.HashMap;
import java.util.Map;

/**
 * The spots drifting wisps are headed for on one server, each by the game
 * tick it was claimed, so two wisps head for no one spot. A Wisps cell is a
 * plain block with no storage, so the claims stand here. A claim lapses once
 * a wisp's longest drift has passed, so a wisp lost to an unloading chunk
 * holds its spot no longer than it could have flown.
 * wisps-travel-as-entities-then-settle-as-glowy-air-blocks
 */
public final class WispClaims {

    /** The ticks a wisp drifts before it gives up, half a minute, which bounds how long its claim stands. */
    public static final int LONGEST_DRIFT_TICKS = 600;

    private final Map<Spot, Long> claims = new HashMap<>();

    /**
     * Whether a wisp is headed for a spot.
     *
     * @param level the spot's level
     * @param spot  the spot
     * @return true while a claim on it stands
     */
    public boolean claimed(Level level, BlockPos spot) {
        Long claimedAt = claims.get(new Spot(level.dimension(), spot));
        return claimedAt != null && level.getGameTime() - claimedAt < LONGEST_DRIFT_TICKS;
    }

    /**
     * Claims a spot for a wisp headed there, dropping the claims that lapsed.
     *
     * @param level the spot's level
     * @param spot  the spot
     */
    public void claim(Level level, BlockPos spot) {
        long now = level.getGameTime();
        claims.values().removeIf(claimedAt -> now - claimedAt >= LONGEST_DRIFT_TICKS);
        claims.put(new Spot(level.dimension(), spot.immutable()), now);
    }

    /**
     * Releases a spot a wisp reached or gave up on.
     *
     * @param level the spot's level
     * @param spot  the spot
     */
    public void release(Level level, BlockPos spot) {
        claims.remove(new Spot(level.dimension(), spot));
    }

    /** Drops every claim, as a server stop does. */
    public void clear() {
        claims.clear();
    }

    /**
     * A spot in a level.
     *
     * @param dimension the level
     * @param spot      the spot's position
     */
    private record Spot(ResourceKey<Level> dimension, BlockPos spot) {
    }
}
