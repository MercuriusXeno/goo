package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.quantum.OutOfPhase;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.LivingEntity;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Puts the host's target out of phase and finishes. A brew phases it for the
 * brew's duration; a step naming a duration phases it for that many ticks,
 * as a mob blob does; with neither it stands as a held effect from the glove
 * until ended. Quantum's phase is {@code phase}, its blob {@code phase duration=600}.
 * phase-shares-a-plane-between-the-phased
 *
 * @param duration the ticks the phase lasts, evaluated when the step runs; empty for a held phase
 */
public record PhaseStep(Optional<Expr> duration) implements Step {

    private static final String NAME = "phase";
    private static final String FIELD_DURATION = "duration";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<PhaseStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.optionalFieldOf(FIELD_DURATION).forGetter(PhaseStep::duration)
    ).apply(inst, PhaseStep::new));

    /**
     * The registered type.
     */
    public static final StepType<PhaseStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<PhaseStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        TargetHost host = context.hostAs(TargetHost.class);
        LivingEntity target = host.target();
        long gameTime = target.level().getGameTime();
        OutOfPhase standing = target.getData(GooAttachments.OUT_OF_PHASE);
        OptionalInt brew = host.brewDuration();
        OutOfPhase laid;
        if (brew.isPresent()) {
            laid = standing.lastingFor(brew.getAsInt(), gameTime);
        } else if (duration.isPresent()) {
            laid = standing.lastingFor(duration.get().evaluateInt(context), gameTime);
        } else {
            laid = OutOfPhase.held();
        }
        target.setData(GooAttachments.OUT_OF_PHASE, laid);
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return duration.stream();
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TARGET);
    }
}
