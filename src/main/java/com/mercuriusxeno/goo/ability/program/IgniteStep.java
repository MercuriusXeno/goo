package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Sets the host's target on fire for a number of seconds and finishes.
 * Blaze ignite is {@code ignite seconds=10} on the struck entity and
 * {@code ignite seconds=5} on each burnable entity around it.
 *
 * @param seconds the burn time in seconds, evaluated when the step runs
 */
public record IgniteStep(Expr seconds) implements Step {

    private static final String NAME = "ignite";
    private static final String FIELD_SECONDS = "seconds";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<IgniteStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_SECONDS).forGetter(IgniteStep::seconds)
    ).apply(inst, IgniteStep::new));

    /**
     * The registered type.
     */
    public static final StepType<IgniteStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<IgniteStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        context.host().target().igniteForSeconds(seconds.evaluateInt(context));
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(seconds);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TARGET);
    }
}
