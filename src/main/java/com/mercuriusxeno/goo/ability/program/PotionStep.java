package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.Goo;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Adds a status effect to the host's target and finishes. The effect is
 * named by id and resolved when the step runs, so the step decodes without a
 * registry behind it; typhoon levitate is
 * {@code potion effect=minecraft:levitation duration=100 amplifier=1}.
 *
 * @param effect    the status effect id
 * @param duration  the duration in ticks, evaluated when the step runs
 * @param amplifier the amplifier, evaluated when the step runs
 * @param visible   whether the effect shows particles and an icon
 */
public record PotionStep(Identifier effect, Expr duration, Expr amplifier, boolean visible) implements Step {

    private static final String NAME = "potion";
    private static final String LOG_UNKNOWN_EFFECT = "Potion step names status effect {}, which no registry holds";
    private static final String FIELD_EFFECT = "effect";
    private static final String FIELD_DURATION = "duration";
    private static final String FIELD_AMPLIFIER = "amplifier";
    private static final String FIELD_VISIBLE = "visible";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<PotionStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Identifier.CODEC.fieldOf(FIELD_EFFECT).forGetter(PotionStep::effect),
            Expr.CODEC.fieldOf(FIELD_DURATION).forGetter(PotionStep::duration),
            Expr.CODEC.optionalFieldOf(FIELD_AMPLIFIER, Expr.literal(0)).forGetter(PotionStep::amplifier),
            Codec.BOOL.optionalFieldOf(FIELD_VISIBLE, true).forGetter(PotionStep::visible)
    ).apply(inst, PotionStep::new));

    /**
     * The registered type.
     */
    public static final StepType<PotionStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<PotionStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        int ticks = duration.evaluateInt(context);
        int level = amplifier.evaluateInt(context);
        Optional<Holder.Reference<MobEffect>> holder = BuiltInRegistries.MOB_EFFECT.get(effect);
        if (holder.isEmpty()) {
            Goo.LOGGER.warn(LOG_UNKNOWN_EFFECT, effect);
            return true;
        }
        context.hostAs(TargetHost.class).target().addEffect(new MobEffectInstance(holder.get(), ticks, level, false, visible));
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
