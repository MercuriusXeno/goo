package com.mercuriusxeno.goo.ability.program;

import org.jspecify.annotations.Nullable;
import java.util.OptionalDouble;

/**
 * What a step sees on one tick: the host, how many ticks the step has run
 * and how many the program has, the sounds the program holds until their
 * frame, and the variable scope that layers the program's counters in
 * front of the host's reads.
 *
 * @param host         the host seam
 * @param stepTicks    ticks the current step has already run, zero on its first tick
 * @param programTicks ticks since the program body started
 * @param heldCues     the sounds the running program holds, or null where no program holds sounds
 */
public record StepContext(StepHost host, int stepTicks, int programTicks, @Nullable HeldCues heldCues)
        implements Variables {

    /**
     * The variable naming ticks since the program started.
     */
    public static final String VAR_TICK = "tick";

    /**
     * A step's context outside a program that holds sounds, where a framed sound plays at once.
     *
     * @param host         the host seam
     * @param stepTicks    ticks the current step has already run
     * @param programTicks ticks since the program body started
     */
    public StepContext(StepHost host, int stepTicks, int programTicks) {
        this(host, stepTicks, programTicks, null);
    }

    /**
     * Plays a sound a frame of ticks from now: at once for frame zero or
     * where no program holds sounds, else held by the program until then.
     * ability-json-names-its-choreography
     *
     * @param cue   the evaluated sound
     * @param frame the ticks to wait before it plays
     */
    public void playSound(SoundCue cue, int frame) {
        if (frame <= 0 || heldCues == null) {
            host.playSound(cue);
        } else {
            heldCues.hold(cue, frame, host);
        }
    }

    /**
     * Returns the host as the capability interface a step needs. The step
     * names that capability in {@link Step#requires()}, and the load check
     * refuses a host type not implementing it, so the cast holds
     * (decision capability-interfaces-derive-host-kind).
     *
     * @param capability the capability interface
     * @param <H>        the capability interface type
     * @return the host as that interface
     */
    public <H extends StepHost> H hostAs(Class<H> capability) {
        return capability.cast(host);
    }

    @Override
    public OptionalDouble read(String name) {
        if (VAR_TICK.equals(name)) {
            return OptionalDouble.of(programTicks);
        }
        return host.read(name);
    }
}
