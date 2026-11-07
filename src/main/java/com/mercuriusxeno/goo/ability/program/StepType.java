package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * A registered step kind: the name the JSON {@code "type"} field carries
 * and the MapCodec that reads the step's own params (decision
 * ability-params-in-datapack: each step's params typed by its own codec).
 * The {@code of} factories build a leaf kind, whose step frame
 * {@link LeafStep} carries once (decision capability-interfaces-derive-host-kind).
 *
 * @param name  the type name as written in the JSON
 * @param codec the codec for the step's params
 * @param <T>   the step class
 */
public record StepType<T extends Step>(String name, MapCodec<T> codec) {

    private static final Set<HostCapability> TARGET = Set.of(HostCapability.TARGET);

    /**
     * Builds a leaf kind whose params are one expression under one field.
     *
     * @param name     the type name as written in the JSON
     * @param field    the field the expression is written under
     * @param requires the capabilities the step needs of its host
     * @param body     what a tick runs
     * @return the leaf kind
     */
    public static LeafStepType<Expr> of(String name, String field, Set<HostCapability> requires,
                                        LeafStepType.Body<Expr> body) {
        return new LeafStepType<>(name, Expr.CODEC.fieldOf(field), amount -> Stream.of(amount), requires, body);
    }

    /**
     * Builds a leaf kind whose params hold no expression.
     *
     * @param name     the type name as written in the JSON
     * @param params   the codec for the params
     * @param requires the capabilities the step needs of its host
     * @param body     what a tick runs
     * @param <P>      the params type
     * @return the leaf kind
     */
    public static <P> LeafStepType<P> of(String name, MapCodec<P> params, Set<HostCapability> requires,
                                         LeafStepType.Body<P> body) {
        return new LeafStepType<>(name, params, unused -> Stream.empty(), requires, body);
    }

    /**
     * Builds a leaf kind acting on its host's target only where the target
     * is of a kind and passes a test, the one rule both its tick and its
     * entity answer read; any other target is left alone.
     * decision steps-declare-the-entities-they-take
     *
     * @param name      the type name as written in the JSON
     * @param params    the codec for the params
     * @param kind      the class of entity the step acts on
     * @param qualifies what the entity must further be
     * @param body      what a tick does to a target the rule keeps
     * @param <P>       the params type
     * @param <E>       the entity kind
     * @return the leaf kind
     */
    public static <P, E extends Entity> LeafStepType<P> onTarget(String name, MapCodec<P> params, Class<E> kind,
                                                                 Predicate<? super E> qualifies, TakenBody<P, E> body) {
        Predicate<Entity> rule = ruleOf(kind, qualifies);
        return of(name, params, TARGET, onTaken(rule, kind, body)).taking(rule);
    }

    /**
     * Builds a leaf kind whose params are one expression under one field,
     * acting on its host's target only where the target is of a kind and
     * passes a test.
     * decision steps-declare-the-entities-they-take
     *
     * @param name      the type name as written in the JSON
     * @param field     the field the expression is written under
     * @param kind      the class of entity the step acts on
     * @param qualifies what the entity must further be
     * @param body      what a tick does to a target the rule keeps
     * @param <E>       the entity kind
     * @return the leaf kind
     */
    public static <E extends Entity> LeafStepType<Expr> onTarget(String name, String field, Class<E> kind,
                                                                 Predicate<? super E> qualifies, TakenBody<Expr, E> body) {
        Predicate<Entity> rule = ruleOf(kind, qualifies);
        return of(name, field, TARGET, onTaken(rule, kind, body)).taking(rule);
    }

    private static <E extends Entity> Predicate<Entity> ruleOf(Class<E> kind, Predicate<? super E> qualifies) {
        return entity -> kind.isInstance(entity) && qualifies.test(kind.cast(entity));
    }

    private static <P, E extends Entity> LeafStepType.Body<P> onTaken(Predicate<Entity> rule, Class<E> kind,
                                                                      TakenBody<P, E> body) {
        return (params, context) -> {
            LivingEntity target = context.hostAs(TargetHost.class).target();
            if (rule.test(target)) {
                body.apply(kind.cast(target), params, context);
            }
            return true;
        };
    }

    /**
     * What a leaf step does to a target its rule keeps.
     *
     * @param <P> the params type
     * @param <E> the entity kind
     */
    @FunctionalInterface
    public interface TakenBody<P, E extends Entity> {

        /**
         * Acts on the target.
         *
         * @param target  the host's target, of the step's kind
         * @param params  the step's params
         * @param context the host, counters and variable scope for this tick
         */
        void apply(E target, P params, StepContext context);
    }
}
