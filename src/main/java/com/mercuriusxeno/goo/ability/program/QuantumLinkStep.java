package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.quantum.FateLinks;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Strikes the host's target with a shared-fate link for the player who threw
 * the goo, and finishes: the first creature struck waits for its pair, and
 * the next one struck shares its fate for the duration. A throw with no
 * player behind it links nothing. Quantum's link is {@code quantum_link duration=1200}.
 * shared-fate-pools-health-and-status
 *
 * @param duration the ticks the link stands once paired, evaluated when the step runs
 */
public record QuantumLinkStep(Expr duration) implements Step {

    private static final String NAME = "quantum_link";
    private static final String FIELD_DURATION = "duration";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<QuantumLinkStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_DURATION).forGetter(QuantumLinkStep::duration)
    ).apply(inst, QuantumLinkStep::new));

    /**
     * The registered type.
     */
    public static final StepType<QuantumLinkStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<QuantumLinkStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        TargetHost host = context.hostAs(TargetHost.class);
        if (host.thrower() instanceof ServerPlayer caster && host.target().level() instanceof ServerLevel level) {
            FateLinks.strike(level, caster, host.target(), duration.evaluateInt(context));
        }
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
