package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import java.util.Set;
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
}
