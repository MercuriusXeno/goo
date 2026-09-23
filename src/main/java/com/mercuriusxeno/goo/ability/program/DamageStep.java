package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Hurts the host's target and finishes. On the struck entity host the
 * target is the entity the blob hit, so the metal javelin is one
 * {@code damage amount=8 source=magic} step. The metal trap's impale is
 * {@code damage amount=6 source=stalagmite knockback=false}, pinning the
 * target where the spike caught it; the crystal cloud's shred is
 * {@code damage amount=1 source=cactus invulnerable_ticks=1}, leaving the
 * target open to the next shred a tick later.
 *
 * @param amount            the damage, evaluated when the step runs
 * @param source            the damage source
 * @param knockback         whether the hit may push the target
 * @param invulnerableTicks the immunity ticks the hit leaves, when set; the source's own otherwise
 */
public record DamageStep(Expr amount, DamageKind source, boolean knockback,
                         Optional<Expr> invulnerableTicks) implements Step {

    private static final String NAME = "damage";
    private static final String FIELD_AMOUNT = "amount";
    private static final String FIELD_SOURCE = "source";
    private static final String FIELD_KNOCKBACK = "knockback";
    private static final String FIELD_INVULNERABLE_TICKS = "invulnerable_ticks";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<DamageStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_AMOUNT).forGetter(DamageStep::amount),
            DamageKind.CODEC.optionalFieldOf(FIELD_SOURCE, DamageKind.MAGIC).forGetter(DamageStep::source),
            Codec.BOOL.optionalFieldOf(FIELD_KNOCKBACK, true).forGetter(DamageStep::knockback),
            Expr.CODEC.optionalFieldOf(FIELD_INVULNERABLE_TICKS).forGetter(DamageStep::invulnerableTicks)
    ).apply(inst, DamageStep::new));

    /**
     * The registered type.
     */
    public static final StepType<DamageStep> TYPE = new StepType<>(NAME, CODEC);

    /**
     * Creates a damage step whose hit may push the target and leaves the
     * source's own immunity, the defaults the JSON reads when both fields
     * are absent.
     *
     * @param amount the damage, evaluated when the step runs
     * @param source the damage source
     */
    public DamageStep(Expr amount, DamageKind source) {
        this(amount, source, true, Optional.empty());
    }

    @Override
    public StepType<DamageStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        context.host().damageTarget(amount.evaluateFloat(context), source, knockback);
        invulnerableTicks.ifPresent(ticks -> context.host().setTargetHurtCooldown(ticks.evaluateInt(context)));
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.concat(Stream.of(amount), invulnerableTicks.stream());
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TARGET);
    }
}
