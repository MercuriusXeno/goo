package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.frost.FrostCurve;
import com.mercuriusxeno.goo.ability.frost.Frozen;
import com.mercuriusxeno.goo.ability.frost.FrozenEvents;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import java.util.Set;
import java.util.stream.Stream;

/**
 * A frost hit on the host's target: the mob's frozen gauge rises by the
 * amount over its max health, and the curve the JSON names sets how a full
 * gauge holds, thaws and weakens it. Frost snap is
 * {@code freeze amount=10 hold=300 thaw=0.005 vulnerability=0.5}. A target
 * that is no mob is left alone.
 * frozen-gauge-per-mob-encases-when-full
 *
 * @param amount how much the hit freezes, in health points, evaluated on the target
 * @param curve  how the gauge holds, thaws and weakens the mob
 */
public record FreezeStep(Expr amount, FrostCurve curve) implements Step {

    private static final String NAME = "freeze";
    private static final String FIELD_AMOUNT = "amount";

    /**
     * Codec for the step's params, the curve's fields inline beside the amount.
     */
    public static final MapCodec<FreezeStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_AMOUNT).forGetter(FreezeStep::amount),
            FrostCurve.CODEC.forGetter(FreezeStep::curve)
    ).apply(inst, FreezeStep::new));

    /**
     * The registered type.
     */
    public static final StepType<FreezeStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<FreezeStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        LivingEntity target = context.hostAs(TargetHost.class).target();
        if (target instanceof Mob mob) {
            Frozen before = mob.getData(GooAttachments.FROZEN);
            float share = Frozen.shareOf(amount.evaluateFloat(context), mob.getMaxHealth());
            FrozenEvents.settle(mob, before, before.add(share, curve, mob.level().getGameTime()));
        }
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
