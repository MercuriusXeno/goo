package com.mercuriusxeno.goo.ability.program;

import java.util.OptionalDouble;

/**
 * What a step sees on one tick: the host, how many ticks the step has run
 * and how many the program has, and the variable scope that layers the
 * program's counters in front of the host's reads.
 *
 * @param host         the host seam
 * @param stepTicks    ticks the current step has already run, zero on its first tick
 * @param programTicks ticks since the program body started
 */
public record StepContext(StepHost host, int stepTicks, int programTicks) implements Variables {

    /**
     * The variable naming ticks since the program started.
     */
    public static final String VAR_TICK = "tick";

    @Override
    public OptionalDouble read(String name) {
        if (VAR_TICK.equals(name)) {
            return OptionalDouble.of(programTicks);
        }
        return host.read(name);
    }
}
