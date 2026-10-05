package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.network.AilmentPayload;
import com.mercuriusxeno.goo.network.EntityVisuals;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.LivingEntity;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Shows a status ailment's overlay on the host's target for a span of ticks
 * and finishes: every client tracking the target, the target among them,
 * draws the ailment's shader over its model. Hex charm is
 * {@code ailment_overlay kind=hex duration="20 * 60 / pow(health, 0.4)"}.
 * Decision ailment-overlay-shader-per-ailment.
 *
 * @param kind     the ailment shown
 * @param duration the duration in ticks, evaluated when the step runs
 */
public record AilmentOverlayStep(AilmentKind kind, Expr duration) implements Step {

    private static final String NAME = "ailment_overlay";
    private static final String FIELD_KIND = "kind";
    private static final String FIELD_DURATION = "duration";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<AilmentOverlayStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            AilmentKind.CODEC.fieldOf(FIELD_KIND).forGetter(AilmentOverlayStep::kind),
            Expr.CODEC.fieldOf(FIELD_DURATION).forGetter(AilmentOverlayStep::duration)
    ).apply(inst, AilmentOverlayStep::new));

    /**
     * The registered type.
     */
    public static final StepType<AilmentOverlayStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<AilmentOverlayStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        LivingEntity target = context.hostAs(TargetHost.class).target();
        EntityVisuals.sendToWatchers(target,
                new AilmentPayload(target.getId(), kind, duration.evaluateInt(context)));
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
