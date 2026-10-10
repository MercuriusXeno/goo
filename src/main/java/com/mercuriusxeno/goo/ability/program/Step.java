package com.mercuriusxeno.goo.ability.program;

import java.util.Optional;
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

    /**
     * Streams each child step with the host kind it runs on, which the
     * load check holds it to. A container running its children on its own
     * host keeps this default; one handing them a host bound to a selected
     * entity names that host's kind.
     *
     * @param host the kind of host this step runs on
     * @return each child with its host kind
     */
    default Stream<HostedStep> hostedChildren(HostKind host) {
        return children().map(child -> new HostedStep(child, host));
    }

    /**
     * Streams each expression with the host kind whose variables it reads,
     * which the load check holds it to. A step evaluating every expression
     * on its own host keeps this default; one evaluating an expression on
     * a selected entity names that host's kind.
     *
     * @param host the kind of host this step runs on
     * @return each expression with its host kind
     */
    default Stream<HostedExpr> hostedExpressions(HostKind host) {
        return expressions().map(expr -> new HostedExpr(expr, host));
    }

    /**
     * Answers whether the marker stands against a punch while this step
     * runs, as a field effect's trap does.
     *
     * @return true when breaking the marker is refused
     */
    default boolean standsAgainstBreaking() {
        return false;
    }

    /**
     * Answers whether the step can act on its host now; a self ability runs
     * and drains only when every step admits, so Fungal Shift aimed at no
     * fungus costs nothing (decision fungal-shift-blinks-to-the-aimed-fungus).
     *
     * @param context the host and variable scope the step would run in
     * @return true when the step can act
     */
    default boolean admits(StepContext context) {
        return true;
    }

    /**
     * The sound a self ability plays when this step refuses it; Fuse with
     * no pair to fuse fizzles (decision fuse-two-books-for-hex-goo).
     *
     * @return the refusal sound, or empty for a silent refusal
     */
    default Optional<SoundCue> refusal() {
        return Optional.empty();
    }

    /**
     * Answers whether the marker stays in the cell it landed in while this
     * step runs, though the block holding it up is gone, as a black hole
     * that consumes its own support does.
     *
     * @return true when the marker never falls while this step runs
     */
    default boolean holdsItsPlace() {
        return false;
    }
}
