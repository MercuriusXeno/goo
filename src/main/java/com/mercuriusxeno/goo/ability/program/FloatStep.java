package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.typhoon.Floating;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Lifts the host's target with levitation that shows no potion particles and
 * marks it floating until the levitation ends, so every client drawing it
 * pulses the mint platform under its feet, and finishes. Typhoon float is
 * {@code float duration=100 amplifier=1}.
 * float-blob-levitates-the-mob
 *
 * @param duration  the ticks the float lasts, evaluated when the step runs
 * @param amplifier the levitation's amplifier, evaluated when the step runs
 */
public record FloatStep(Expr duration, Expr amplifier) implements Step {

    private static final String NAME = "float";
    private static final String FIELD_DURATION = "duration";
    private static final String FIELD_AMPLIFIER = "amplifier";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<FloatStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_DURATION).forGetter(FloatStep::duration),
            Expr.CODEC.optionalFieldOf(FIELD_AMPLIFIER, Expr.literal(0)).forGetter(FloatStep::amplifier)
    ).apply(inst, FloatStep::new));

    /**
     * The registered type.
     */
    public static final StepType<FloatStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<FloatStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        LivingEntity target = context.hostAs(TargetHost.class).target();
        int ticks = duration.evaluateInt(context);
        target.addEffect(new MobEffectInstance(MobEffects.LEVITATION, ticks, amplifier.evaluateInt(context),
                false, false));
        target.setData(GooAttachments.FLOATING, new Floating(target.level().getGameTime() + ticks));
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(duration, amplifier);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TARGET);
    }
}
