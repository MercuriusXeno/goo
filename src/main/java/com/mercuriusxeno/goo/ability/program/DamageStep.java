package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Hurts the host's target and finishes. On the struck entity host the
 * target is the entity the blob hit, so the metal javelin is one
 * {@code damage amount=8 source=magic} step. The metal trap's impale is
 * {@code damage amount=6 source=stalagmite knockback=false}, pinning the
 * target where the spike caught it.
 *
 * @param amount    the damage, evaluated when the step runs
 * @param source    the damage source
 * @param knockback whether the hit may push the target
 */
public record DamageStep(Expr amount, DamageKind source, boolean knockback) implements Step {

    private static final String NAME = "damage";
    private static final String FIELD_AMOUNT = "amount";
    private static final String FIELD_SOURCE = "source";
    private static final String FIELD_KNOCKBACK = "knockback";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<DamageStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_AMOUNT).forGetter(DamageStep::amount),
            DamageKind.CODEC.optionalFieldOf(FIELD_SOURCE, DamageKind.MAGIC).forGetter(DamageStep::source),
            Codec.BOOL.optionalFieldOf(FIELD_KNOCKBACK, true).forGetter(DamageStep::knockback)
    ).apply(inst, DamageStep::new));

    /**
     * The registered type.
     */
    public static final StepType<DamageStep> TYPE = new StepType<>(NAME, CODEC);

    /**
     * Creates a damage step whose hit may push the target, the default
     * the JSON reads when {@code knockback} is absent.
     *
     * @param amount the damage, evaluated when the step runs
     * @param source the damage source
     */
    public DamageStep(Expr amount, DamageKind source) {
        this(amount, source, true);
    }

    @Override
    public StepType<DamageStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        context.host().damageTarget(amount.evaluateFloat(context), source, knockback);
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
