package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.bio.BioToxinEffect;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Bio's step: inflicts the toxin on the host's target, raising its amplitude
 * by one per application up to the cap and refreshing its duration every
 * time, and writes the share of max health it takes a second where the
 * toxin reads it. Leaf Bio is
 * {@code toxin effect=goo:bio_toxin share_per_second=0.06 duration=100 max_amplitude=2}.
 * bio-toxin-stacks-to-amplitude-two
 *
 * @param effect         the toxin's status effect id
 * @param sharePerSecond the share of max health taken a second at amplitude one
 * @param duration       the duration in ticks every application refreshes to
 * @param maxAmplitude   the highest amplitude repeated applications reach
 */
public record ToxinStep(Identifier effect, Expr sharePerSecond, Expr duration, int maxAmplitude) implements Step {

    private static final String NAME = "toxin";
    private static final String LOG_UNKNOWN_EFFECT = "Toxin step names status effect {}, which no registry holds";
    private static final String FIELD_EFFECT = "effect";
    private static final String FIELD_SHARE = "share_per_second";
    private static final String FIELD_DURATION = "duration";
    private static final String FIELD_MAX_AMPLITUDE = "max_amplitude";
    /** The amplifier of a mob the toxin has not reached, one below the first application's. */
    private static final int UNTOXINED = -1;

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<ToxinStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Identifier.CODEC.fieldOf(FIELD_EFFECT).forGetter(ToxinStep::effect),
            Expr.CODEC.fieldOf(FIELD_SHARE).forGetter(ToxinStep::sharePerSecond),
            Expr.CODEC.fieldOf(FIELD_DURATION).forGetter(ToxinStep::duration),
            Codec.INT.fieldOf(FIELD_MAX_AMPLITUDE).forGetter(ToxinStep::maxAmplitude)
    ).apply(inst, ToxinStep::new));

    /**
     * The registered type.
     */
    public static final StepType<ToxinStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<ToxinStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        Optional<Holder.Reference<MobEffect>> holder = BuiltInRegistries.MOB_EFFECT.get(effect);
        if (holder.isEmpty()) {
            Goo.LOGGER.warn(LOG_UNKNOWN_EFFECT, effect);
            return true;
        }
        LivingEntity target = context.hostAs(TargetHost.class).target();
        MobEffectInstance standing = target.getEffect(holder.get());
        int amplifier = nextAmplifier(standing == null ? UNTOXINED : standing.getAmplifier(), maxAmplitude);
        target.setData(GooAttachments.ENTITY_COUNTERS, target.getData(GooAttachments.ENTITY_COUNTERS)
                .withValue(BioToxinEffect.SHARE_PER_SECOND, sharePerSecond.evaluate(context)));
        target.addEffect(new MobEffectInstance(holder.get(), duration.evaluateInt(context), amplifier));
        return true;
    }

    /**
     * The amplifier an application leaves: one above the standing one, never
     * past the cap's.
     *
     * @param standing     the standing amplifier, -1 for a mob the toxin has not reached
     * @param maxAmplitude the highest amplitude, the amplifier plus one
     * @return the amplifier to apply
     */
    static int nextAmplifier(int standing, int maxAmplitude) {
        return Math.min(standing + 1, maxAmplitude - 1);
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(sharePerSecond, duration);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TARGET);
    }
}
