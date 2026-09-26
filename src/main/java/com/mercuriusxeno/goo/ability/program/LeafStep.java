package com.mercuriusxeno.goo.ability.program;

import java.util.Set;
import java.util.stream.Stream;

/**
 * A leaf step: the params its type's codec read, run by the body its
 * {@link LeafStepType} carries, so the step frame (type, tick, expressions,
 * requires) is written once for every leaf kind (decision
 * capability-interfaces-derive-host-kind).
 *
 * @param leaf   the leaf type carrying the frame
 * @param params the step's params
 * @param <P>    the params type
 */
public record LeafStep<P>(LeafStepType<P> leaf, P params) implements Step {

    @Override
    public StepType<LeafStep<P>> type() {
        return leaf.type();
    }

    @Override
    public boolean tick(StepContext context) {
        return leaf.body().tick(params, context);
    }

    @Override
    public Stream<Expr> expressions() {
        return leaf.expressions().apply(params);
    }

    @Override
    public Set<HostCapability> requires() {
        return leaf.requires();
    }
}
