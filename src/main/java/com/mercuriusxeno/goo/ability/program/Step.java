package com.mercuriusxeno.goo.ability.program;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
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

    /**
     * The RGB a bare prism's quartz warms toward while a stream charges it
     * into this step's combo, the color the converted prism wears.
     * prism-conversion-creeps-over-the-hold
     *
     * @return the tint, or empty where the combo keeps the plain quartz
     */
    default OptionalInt prismTint() {
        return OptionalInt.empty();
    }

    /**
     * Whether the step acts on an entity, read alike by the server and by
     * the glove's aim, which outlines only what the selected ability takes.
     * A container takes what any of its children take; any other step takes
     * a living entity unless it names a narrower rule, or none where it acts
     * on no entity.
     * decision steps-declare-the-entities-they-take
     *
     * @param entity the candidate
     * @return true where the step acts on it
     */
    default boolean takesEntity(Entity entity) {
        List<Step> children = children().toList();
        return children.isEmpty() ? entity instanceof LivingEntity : takenByAny(children, entity);
    }

    /**
     * Whether any step of a program takes an entity: an ability's answer is
     * the union of its steps.
     * decision steps-declare-the-entities-they-take
     *
     * @param program the steps
     * @param entity  the candidate
     * @return true where a step acts on it
     */
    static boolean takenByAny(List<Step> program, Entity entity) {
        return program.stream().anyMatch(step -> step.takesEntity(entity));
    }

    /**
     * The steps of a program that act on the entity the glove aims at. A
     * self ability runs on its caster, so its steps act on the caster and
     * only a step reaching past it to the aimed entity counts; any other
     * program acts where it lands, so every step counts.
     * decision steps-declare-the-entities-they-take
     *
     * @param program         the ability's steps
     * @param runsOnTheCaster whether the ability is delivered to its caster
     * @return the steps whose answer the aim reads
     */
    static List<Step> reachingTheAim(List<Step> program, boolean runsOnTheCaster) {
        return runsOnTheCaster ? program.stream().filter(ReachesTheAim.class::isInstance).toList() : program;
    }

    /**
     * Whether a container filtering its selection takes an entity: one
     * every filter keeps and a child step takes.
     * decision steps-declare-the-entities-they-take
     *
     * @param where  the container's filters
     * @param steps  the children run on what the filters keep
     * @param entity the candidate
     * @return true where the container acts on it
     */
    static boolean takenThrough(List<EntityFilter> where, List<Step> steps, Entity entity) {
        return EntityScan.passes(entity, Set.copyOf(where), null) && takenByAny(steps, entity);
    }
}
