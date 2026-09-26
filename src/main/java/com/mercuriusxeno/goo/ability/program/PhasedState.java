package com.mercuriusxeno.goo.ability.program;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * What a {@link PhasedStep} carries across ticks: which phase runs, how
 * many ticks it has run and how long it lasts. A step is an immutable
 * definition, so the host keeps this run state for it (capability
 * {@link HostCapability#PHASED}); the marker keeps it on its block entity,
 * which saves it and syncs it to the client, where the nether black hole
 * reads the phase and its progress, and reads the radius off the step in
 * the synced ability definition (decision capability-interfaces-derive-host-kind).
 */
public final class PhasedState {

    private static final String TAG_NAME = "PhaseName";
    private static final String TAG_INDEX = "PhaseIndex";
    private static final String TAG_TICKS = "PhaseTicks";
    private static final String TAG_DURATION = "PhaseDuration";
    private static final String NOT_RUNNING = "";

    private String name = NOT_RUNNING;
    private int index;
    private int ticks;
    private int duration;

    /**
     * Returns the name of the phase now running, empty while no phased
     * step runs.
     *
     * @return the phase name
     */
    public String name() {
        return name;
    }

    /**
     * Answers whether a phased step is running.
     *
     * @return true while a phase holds the cursor
     */
    public boolean isRunning() {
        return !name.isEmpty();
    }

    /**
     * Returns the index of the phase now running.
     *
     * @return the phase index, from zero
     */
    public int index() {
        return index;
    }

    /**
     * Returns how many ticks the phase now running has run its body.
     *
     * @return the phase ticks, zero before the phase is entered
     */
    public int ticks() {
        return ticks;
    }

    /**
     * Returns how many ticks the phase now running lasts, fixed when it
     * was entered.
     *
     * @return the phase duration
     */
    public int duration() {
        return duration;
    }


    /**
     * Returns how far through its duration the phase now running is.
     *
     * @return the progress in [0, 1], zero for a phase with no duration
     */
    public float progress() {
        return duration > 0 ? Math.min(1f, (float) ticks / duration) : 0f;
    }

    /**
     * Enters a phase: names it and fixes its duration.
     *
     * @param phaseName the phase's name
     * @param length    the ticks the phase lasts
     */
    public void enter(String phaseName, int length) {
        this.name = phaseName;
        this.duration = length;
    }

    /**
     * Counts one tick of the phase's body.
     */
    public void countTick() {
        ticks++;
    }

    /**
     * Moves the cursor to the next phase, named but not yet entered.
     *
     * @param nextName the next phase's name
     */
    public void advance(String nextName) {
        index++;
        ticks = 0;
        duration = 0;
        name = nextName;
    }

    /**
     * Clears the cursor once the last phase has left, so a later phased
     * step starts from its first phase.
     */
    public void finish() {
        name = NOT_RUNNING;
        index = 0;
        ticks = 0;
        duration = 0;
    }

    /**
     * Writes the state onto the block entity's value stream.
     *
     * @param output the value output
     */
    public void save(ValueOutput output) {
        output.putString(TAG_NAME, name);
        output.putInt(TAG_INDEX, index);
        output.putInt(TAG_TICKS, ticks);
        output.putInt(TAG_DURATION, duration);
    }

    /**
     * Reads the state back from the block entity's value stream.
     *
     * @param input the value input
     */
    public void load(ValueInput input) {
        name = input.getStringOr(TAG_NAME, NOT_RUNNING);
        index = input.getIntOr(TAG_INDEX, 0);
        ticks = input.getIntOr(TAG_TICKS, 0);
        duration = input.getIntOr(TAG_DURATION, 0);
    }
}
