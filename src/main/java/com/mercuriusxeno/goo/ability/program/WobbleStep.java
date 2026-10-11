package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.weird.Wobbled;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.LivingEntity;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Wobbles the host's target and finishes: until the wobble fades, the
 * target's attacks deal no damage and knock their target back by the damage
 * they would have dealt.
 * weird-bounces-and-softens-harm
 *
 * @param duration the ticks the wobble lasts, evaluated when the step runs
 */
public record WobbleStep(Expr duration) implements Step {

    private static final String NAME = "wobble";
    private static final String FIELD_DURATION = "duration";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<WobbleStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_DURATION).forGetter(WobbleStep::duration)
    ).apply(inst, WobbleStep::new));

    /**
     * The registered type.
     */
    public static final StepType<WobbleStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<WobbleStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        LivingEntity target = context.hostAs(TargetHost.class).target();
        long expiresAt = target.level().getGameTime() + duration.evaluateInt(context);
        target.setData(GooAttachments.WOBBLED, new Wobbled(expiresAt));
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(duration);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TARGET);
    }
}
