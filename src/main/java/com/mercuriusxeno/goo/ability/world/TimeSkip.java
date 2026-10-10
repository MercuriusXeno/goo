package com.mercuriusxeno.goo.ability.world;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.clock.WorldClock;
import java.util.Optional;

/**
 * Moves a world's clock forward, never backward, without touching how the
 * world ticks: the day time jumps, and every online player's time since
 * rest winds back by the ticks skipped, so phantoms keep to the time the
 * player lived through rather than the clock's jump.
 * timekeeper-prism-banks-ticks-forward-only
 */
public final class TimeSkip {

    private static final String BACKWARD = "Time only moves forward; refused a skip of ";

    private TimeSkip() {
    }

    /**
     * The day time after a skip.
     *
     * @param dayTime the day time now
     * @param ticks   the ticks skipped
     * @return the day time moved forward by the ticks
     * @throws IllegalArgumentException for a skip backward
     */
    public static long dayTimeAfter(long dayTime, long ticks) {
        if (ticks < 0) {
            throw new IllegalArgumentException(BACKWARD + ticks);
        }
        return dayTime + ticks;
    }

    /**
     * A player's time since rest after a skip: wound back by the ticks
     * skipped, never below zero.
     *
     * @param timeSinceRest the player's time since rest now
     * @param ticks         the ticks skipped
     * @return the time since rest after the skip
     */
    public static int restAfterSkip(int timeSinceRest, long ticks) {
        return (int) Math.max(0L, timeSinceRest - ticks);
    }

    /**
     * Skips a level's own clock forward and winds every online player's time
     * since rest back by the same ticks; a level with no clock of its own,
     * as the nether, skips nothing.
     *
     * @param level the level
     * @param ticks the ticks skipped
     * @return true when the level's clock moved
     */
    public static boolean skip(ServerLevel level, long ticks) {
        Optional<Holder<WorldClock>> clock = level.dimensionType().defaultClock();
        if (clock.isEmpty()) {
            return false;
        }
        ServerClockManager clocks = level.clockManager();
        clocks.setTotalTicks(clock.get(), dayTimeAfter(clocks.getTotalTicks(clock.get()), ticks));
        Stat<Identifier> sinceRest = Stats.CUSTOM.get(Stats.TIME_SINCE_REST);
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            int now = player.getStats().getValue(sinceRest);
            player.getStats().setValue(player, sinceRest, restAfterSkip(now, ticks));
        }
        return true;
    }
}
