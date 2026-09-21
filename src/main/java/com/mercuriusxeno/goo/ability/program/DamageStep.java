package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Hurts the host's target and finishes. On the struck entity host the
 * target is the entity the blob hit, so the metal javelin is one
 * {@code damage amount=8 source=magic} step.
 *
 * @param amount the damage, evaluated when the step runs
 * @param source the damage source
 */
public record DamageStep(Expr amount, DamageKind source) implements Step {

    private static final String NAME = "damage";
    private static final String FIELD_AMOUNT = "amount";
    private static final String FIELD_SOURCE = "source";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<DamageStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_AMOUNT).forGetter(DamageStep::amount),
            DamageKind.CODEC.optionalFieldOf(FIELD_SOURCE, DamageKind.MAGIC).forGetter(DamageStep::source)
    ).apply(inst, DamageStep::new));

    /**
     * The registered type.
     */
    public static final StepType<DamageStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<DamageStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        context.host().damageTarget(amount.evaluateFloat(context), source);
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(amount);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TARGET);
    }
}
