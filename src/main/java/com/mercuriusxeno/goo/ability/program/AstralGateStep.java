package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Opens Astral's gate where the blob lands and finishes: a portal over the
 * struck surface partnered with a mirror in the lunar dimension by night or
 * the solar dimension by day, both changing back to the blocks they covered
 * after the lifetime. Astral's gate is {@code astral_gate lifetime=1200}.
 * decision astral-visits-lunar-and-solar-dimensions
 *
 * @param lifetime the ticks the pair stands, evaluated when the step runs
 */
public record AstralGateStep(Expr lifetime) implements Step {

    private static final String NAME = "astral_gate";
    private static final String FIELD_LIFETIME = "lifetime";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<AstralGateStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_LIFETIME).forGetter(AstralGateStep::lifetime)
    ).apply(inst, AstralGateStep::new));

    /**
     * The registered type.
     */
    public static final StepType<AstralGateStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<AstralGateStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        context.hostAs(GateHost.class).openAstralGate(lifetime.evaluateInt(context));
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(lifetime);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.ASTRAL_GATE);
    }
}
