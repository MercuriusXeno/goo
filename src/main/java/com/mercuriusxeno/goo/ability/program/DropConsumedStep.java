package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Drops the goo total the host consumed as blob items at the anchor,
 * emptying the total, and finishes. The nether black hole pops what it
 * consumed once it has contracted: {@code drop_consumed}.
 */
public record DropConsumedStep() implements Step {

    private static final String NAME = "drop_consumed";

    /**
     * Codec for the step, which takes no params.
     */
    public static final MapCodec<DropConsumedStep> CODEC = MapCodec.unit(DropConsumedStep::new);

    /**
     * The registered type.
     */
    public static final StepType<DropConsumedStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<DropConsumedStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        context.host().dropConsumedGoo();
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.empty();
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.CONSUMED_GOO);
    }
}
