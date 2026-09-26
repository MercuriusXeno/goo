package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Detonates at the host's anchor and finishes. The power is an expression
 * over the host, so {@code "2 + 1 * (stacks - 1)"} scales with the blobs
 * stacked on the marker.
 *
 * @param power the explosion power, evaluated when the step runs
 * @param mode  how blocks are treated
 */
public record ExplodeStep(Expr power, ExplosionMode mode) implements Step {

    private static final String NAME = "explode";
    private static final String FIELD_POWER = "power";
    private static final String FIELD_INTERACTION = "interaction";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<ExplodeStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_POWER).forGetter(ExplodeStep::power),
            ExplosionMode.CODEC.optionalFieldOf(FIELD_INTERACTION, ExplosionMode.TNT).forGetter(ExplodeStep::mode)
    ).apply(inst, ExplodeStep::new));

    /**
     * The registered type.
     */
    public static final StepType<ExplodeStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<ExplodeStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        context.hostAs(ExplodeHost.class).explode(power.evaluateFloat(context), mode);
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(power);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.EXPLODE);
    }
}
