package com.mercuriusxeno.goo.block.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import java.util.HashMap;
import java.util.Map;

/**
 * The spots wisps are headed for or have settled in on one server, each by
 * the game tick it was claimed: the wisps' shared knowledge of where their
 * light will fall, so no wisp heads for a spot another wisp will light
 * before it arrives. A Wisps cell is a plain block with no storage, so the
 * claims stand here. A claim lapses once a wisp's longest drift has passed,
 * by when a settled wisp's light stands in the world itself and a wisp lost
 * to an unloading chunk holds its spot no longer than it could have flown.
 * wisps-travel-as-entities-then-settle-as-glowy-air-blocks
 */
public final class WispClaims {

    /** The ticks a wisp drifts before it gives up, half a minute, which bounds how long its claim stands. */
    public static final int LONGEST_DRIFT_TICKS = 600;

    private final Map<Spot, Long> claims = new HashMap<>();

    /**
     * Whether a wisp's light will reach a spot: a standing claim lies within
     * a settled wisp's lit steps of it, so the spot will read lit by the
     * time a wisp heading there now arrives, whatever the world reads today.
     * Light falls one level a step, so the steps count along each axis.
     * operator UAT 2026-11-16: wisps vanished arriving at spots another wisp had lit, the wisps a hive mind
     *
     * @param level    the spot's level
     * @param spot     the spot
     * @param litSteps how many steps out a settled wisp keeps a cell lit
     * @return true where a claimed wisp's light will reach the spot
     */
    public boolean foreseesLight(Level level, BlockPos spot, int litSteps) {
        long now = level.getGameTime();
        for (Map.Entry<Spot, Long> claim : claims.entrySet()) {
            if (claim.getKey().dimension().equals(level.dimension()) && now - claim.getValue() < LONGEST_DRIFT_TICKS
                    && claim.getKey().spot().distManhattan(spot) <= litSteps) {
                return true;
            }
        }
        return false;
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
     * Releases a spot a wisp gave up on, which its light will never reach.
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
