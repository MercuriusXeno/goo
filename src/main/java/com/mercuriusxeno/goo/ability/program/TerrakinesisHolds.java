package com.mercuriusxeno.goo.ability.program;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Each player's Terrakinesis grabs on the server: the tick their current
 * wind-up began, so every grab winds up afresh, even one inside a hold that
 * already grabbed and threw, and the tick a throw lets them grab again, so no
 * new chunk comes up while the thrown one flies.
 * decision terrakinesis-shakes-the-blocks-before-the-tear
 * decision rock-grabs-hunks-of-rock-goo-blocks-as-a-huckable-entity
 */
public final class TerrakinesisHolds {

    /** A wind-up whose player sent no hold tick for longer than this has been let go. */
    private static final long LET_GO_TICKS = 2;

    private static final Map<UUID, WindUp> WIND_UPS = new HashMap<>();
    private static final Map<UUID, Long> READY_AT = new HashMap<>();

    /**
     * A wind-up under way.
     *
     * @param start    the game time it began
     * @param lastTick the game time its player last held
     */
    private record WindUp(long start, long lastTick) {
    }

    private TerrakinesisHolds() {
    }

    /**
     * Counts one held tick of a player's wind-up, beginning a new one where none
     * runs or the last was let go.
     *
     * @param player the grabbing player's id
     * @param now    the game time
     * @return the wind-up's tick count, 1 on its first tick
     */
    static int windUpTick(UUID player, long now) {
        WindUp last = WIND_UPS.get(player);
        long start = last == null || now - last.lastTick() > LET_GO_TICKS ? now : last.start();
        WIND_UPS.put(player, new WindUp(start, now));
        return (int) (now - start) + 1;
    }

    /**
     * The wind-up a player has running, as their client is told it: none
     * from the tear on, while a hunk is held, or while a throw cools down.
     *
     * @param player the player's id
     * @return the wind-up, or {@link TerrakinesisWindUp#NONE}
     */
    public static TerrakinesisWindUp windUpOf(UUID player) {
        WindUp running = WIND_UPS.get(player);
        return running == null ? TerrakinesisWindUp.NONE : new TerrakinesisWindUp(running.start());
    }

    /**
     * Ends a player's wind-up, as their chunk comes up.
     *
     * @param player the player's id
     */
    static void grabbed(UUID player) {
        WIND_UPS.remove(player);
    }

    /**
     * Starts a player's cooldown, as they throw their chunk.
     *
     * @param player the player's id
     * @param now    the game time
     */
    public static void threw(UUID player, long now) {
        WIND_UPS.remove(player);
        READY_AT.put(player, now + TerrakinesisStep.THROW_COOLDOWN_TICKS);
    }

    /**
     * Whether a player's throw still holds off their next grab.
     *
     * @param player the player's id
     * @param now    the game time
     * @return true inside the cooldown
     */
    static boolean coolingDown(UUID player, long now) {
        return now < READY_AT.getOrDefault(player, Long.MIN_VALUE);
    }
}
