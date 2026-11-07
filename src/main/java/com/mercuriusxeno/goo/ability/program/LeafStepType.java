package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * A leaf step kind: its registered {@link StepType}, the capabilities it
 * needs, the expressions its params hold and the body a tick runs.
 * {@link StepType#of} builds one, so a new leaf step is its body and one
 * definition line (decision capability-interfaces-derive-host-kind).
 *
 * @param <P> the params type
 */
public final class LeafStepType<P> {

    private final String name;
    private final MapCodec<P> params;
    private final StepType<LeafStep<P>> type;
    private final Set<HostCapability> requires;
    private final Function<P, Stream<Expr>> expressions;
    private final Body<P> body;
    private final Predicate<Entity> takes;

    /**
     * Creates a leaf kind taking a living entity, whose codec reads the
     * params and wraps them in a step of this kind.
     *
     * @param name        the type name as written in the JSON
     * @param params      the codec for the params
     * @param expressions the expressions the params hold
     * @param requires    the capabilities the step needs of its host
     * @param body        what a tick runs
     */
    LeafStepType(String name, MapCodec<P> params, Function<P, Stream<Expr>> expressions,
                 Set<HostCapability> requires, Body<P> body) {
        this(name, params, expressions, requires, body, LivingEntity.class::isInstance);
    }

    private LeafStepType(String name, MapCodec<P> params, Function<P, Stream<Expr>> expressions,
                         Set<HostCapability> requires, Body<P> body, Predicate<Entity> takes) {
        this.name = name;
        this.params = params;
        this.type = new StepType<>(name, params.xmap(this::step, LeafStep::params));
        this.requires = Set.copyOf(requires);
        this.expressions = expressions;
        this.body = body;
        this.takes = takes;
    }

    /**
     * This kind with the entities its steps act on named, in place of a
     * living entity.
     * decision steps-declare-the-entities-they-take
     *
     * @param rule what a step of this kind acts on
     * @return the kind taking what the rule keeps
     */
    LeafStepType<P> taking(Predicate<Entity> rule) {
        return new LeafStepType<>(name, params, expressions, requires, body, rule);
    }

    /**
     * This kind acting on no entity: it waits, or changes blocks alone.
     * decision steps-declare-the-entities-they-take
     *
     * @return the kind taking no entity
     */
    LeafStepType<P> takingNone() {
        return taking(entity -> false);
    }

    /**
     * Answers whether a step of this kind acts on an entity.
     *
     * @param entity the candidate
     * @return true where the kind's rule keeps it
     */
    public boolean takes(Entity entity) {
        return takes.test(entity);
    }

    /**
     * Builds a step of this kind over its params.
     *
     * @param params the params
     * @return the step
     */
    public LeafStep<P> step(P params) {
        return new LeafStep<>(this, params);
    }

    /**
     * Returns the registered type whose codec reads steps of this kind.
     *
     * @return the step type
     */
    public StepType<LeafStep<P>> type() {
        return type;
    }

    /**
     * Returns the capabilities a step of this kind needs of its host.
     *
     * @return the required capabilities
     */
    public Set<HostCapability> requires() {
        return requires;
    }

    /**
     * Returns the function streaming the expressions a step's params hold.
     *
     * @return the expressions function
     */
    public Function<P, Stream<Expr>> expressions() {
        return expressions;
    }

    /**
     * Returns the body a step of this kind runs each tick.
     *
     * @return the body
     */
    public Body<P> body() {
        return body;
    }

    /**
     * What a leaf step does on one tick.
     *
     * @param <P> the params type
     */
    @FunctionalInterface
    public interface Body<P> {

        /**
         * Runs one tick of the step.
         *
         * @param params  the step's params
         * @param context the host, counters and variable scope for this tick
         * @return true when the step is finished
         */
        boolean tick(P params, StepContext context);
    }
}
