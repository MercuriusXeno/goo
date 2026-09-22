package com.mercuriusxeno.goo.ability.program;

import java.util.Set;
import java.util.stream.Stream;

/**
 * One step of a step program: an immutable definition read by its
 * {@link StepType}'s codec and shared by every marker running the ability.
 * Per-run state lives in the {@link ProgramBehavior} that ticks it, so a
 * step reads its progress from the {@link StepContext} rather than fields.
 */
public interface Step {

    /**
     * Returns the type whose codec wrote this step, which the dispatch
     * codec writes under {@code "type"}.
     *
     * @return the step type
     */
    StepType<? extends Step> type();

    /**
     * Runs one tick of the step. An instant step acts and answers true on
     * its first tick; a waiting step answers false until its condition
     * holds. The runtime starts the next step the same tick a step
     * finishes.
     *
     * @param context the host, counters and variable scope for this tick
     * @return true when the step is finished
     */
    boolean tick(StepContext context);

    /**
     * Streams every expression the step evaluates, for load-time checks
     * of the variables a host must bind.
     *
     * @return the expressions
     */
    Stream<Expr> expressions();

    /**
     * Names the capabilities the step needs of its host; a host lacking
     * one refuses the program at load.
     *
     * @return the required capabilities
     */
    Set<HostCapability> requires();

    /**
     * Streams the child steps a container step holds, so the load check
     * reaches every step of the tree; a leaf step streams nothing.
     *
     * @return the child steps
     */
    default Stream<Step> children() {
        return Stream.empty();
    }
}
