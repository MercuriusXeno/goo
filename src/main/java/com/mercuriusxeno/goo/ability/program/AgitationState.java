package com.mercuriusxeno.goo.ability.program;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * What an {@link AgitateStep} carries across ticks: the interval between
 * spawn attempts now standing and the ticks left until the next. The marker
 * keeps it on its block entity, which saves it and syncs it to the client,
 * where the agitator prism pulses in time with it.
 * agitator-prism-quickens-until-a-spawn
 */
public final class AgitationState {

    private static final String TAG_INTERVAL = "AgitationInterval";
    private static final String TAG_COUNTDOWN = "AgitationCountdown";

    private int interval;
    private int countdown;

    /**
     * The interval after an attempt: back to the start after a spawn,
     * otherwise shrunk by the factor, never under the floor.
     *
     * @param interval the interval the attempt closed
     * @param spawned  whether the attempt spawned a monster
     * @param start    the interval an agitator starts at
     * @param shrink   the share of the interval a failed attempt keeps, 0 to 1
     * @param floor    the shortest interval
     * @return the next interval
     */
    public static int nextInterval(int interval, boolean spawned, int start, double shrink, int floor) {
        if (spawned) {
            return start;
        }
        return Math.max(floor, (int) Math.floor(interval * shrink));
    }

    /**
     * Starts the countdown at the start interval on the agitator's first tick.
     *
     * @param start the interval an agitator starts at
     */
    public void startIfIdle(int start) {
        if (interval <= 0) {
            interval = start;
            countdown = start;
        }
    }

    /**
     * Counts one tick down.
     *
     * @return true when the countdown has run out and an attempt is due
     */
    public boolean tickDown() {
        countdown--;
        return countdown <= 0;
    }

    /**
     * Restarts the countdown at a new interval.
     *
     * @param next the interval until the next attempt
     */
    public void restart(int next) {
        interval = next;
        countdown = next;
    }

    /**
     * @return the interval between attempts now standing, zero before the agitator starts
     */
    public int interval() {
        return interval;
    }

    /**
     * @return the ticks left until the next attempt
     */
    public int countdown() {
        return countdown;
    }

    /**
     * Restores the state from persistent data.
     *
     * @param input the value input to read from
     */
    public void load(ValueInput input) {
        interval = input.getIntOr(TAG_INTERVAL, 0);
        countdown = input.getIntOr(TAG_COUNTDOWN, 0);
    }

    /**
     * Writes the state to persistent data, nothing while the agitator is idle.
     *
     * @param output the value output to write to
     */
    public void save(ValueOutput output) {
        if (interval > 0) {
            output.putInt(TAG_INTERVAL, interval);
            output.putInt(TAG_COUNTDOWN, countdown);
        }
    }
}
