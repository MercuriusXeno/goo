package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Spawns a burst of particles at an anchor and finishes. {@code spread}
 * sets every axis; {@code spread_along} and {@code spread_across} each
 * override it for the host's axis and the axes beside it. Glow laser is
 * {@code particles id=minecraft:crit count=10 at=target spread=0.5 speed=0.1}.
 *
 * @param particle     the particle type id
 * @param at           the anchor the burst centers on
 * @param count        how many particles, evaluated when the step runs
 * @param spread       the spread on every axis the split fields leave unset
 * @param spreadAlong  the spread along the host's axis, when set
 * @param spreadAcross the spread across the host's axis, when set
 * @param speed        the particle speed
 * @param lift         how far above the anchor the burst centers
 */
public record ParticlesStep(Identifier particle, FxAnchor at, Expr count, Expr spread, Optional<Expr> spreadAlong,
                            Optional<Expr> spreadAcross, Expr speed, Expr lift) implements Step {

    private static final String NAME = "particles";
    private static final String FIELD_ID = "id";
    private static final String FIELD_AT = "at";
    private static final String FIELD_COUNT = "count";
    private static final String FIELD_SPREAD = "spread";
    private static final String FIELD_SPREAD_ALONG = "spread_along";
    private static final String FIELD_SPREAD_ACROSS = "spread_across";
    private static final String FIELD_SPEED = "speed";
    private static final String FIELD_LIFT = "lift";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<ParticlesStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Identifier.CODEC.fieldOf(FIELD_ID).forGetter(ParticlesStep::particle),
            FxAnchor.CODEC.optionalFieldOf(FIELD_AT, FxAnchor.HOST).forGetter(ParticlesStep::at),
            Expr.CODEC.optionalFieldOf(FIELD_COUNT, Expr.literal(1)).forGetter(ParticlesStep::count),
            Expr.CODEC.optionalFieldOf(FIELD_SPREAD, Expr.literal(0)).forGetter(ParticlesStep::spread),
            Expr.CODEC.optionalFieldOf(FIELD_SPREAD_ALONG).forGetter(ParticlesStep::spreadAlong),
            Expr.CODEC.optionalFieldOf(FIELD_SPREAD_ACROSS).forGetter(ParticlesStep::spreadAcross),
            Expr.CODEC.optionalFieldOf(FIELD_SPEED, Expr.literal(0)).forGetter(ParticlesStep::speed),
            Expr.CODEC.optionalFieldOf(FIELD_LIFT, Expr.literal(0)).forGetter(ParticlesStep::lift)
    ).apply(inst, ParticlesStep::new));

    /**
     * The registered type.
     */
    public static final StepType<ParticlesStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<ParticlesStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        double everyAxis = spread.evaluate(context);
        ParticleBurst burst = new ParticleBurst(particle, count.evaluateInt(context),
                spreadAlong.map(expr -> expr.evaluate(context)).orElse(everyAxis),
                spreadAcross.map(expr -> expr.evaluate(context)).orElse(everyAxis),
                speed.evaluate(context), lift.evaluate(context));
        context.host().spawnParticles(burst);
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.concat(Stream.of(count, spread, speed, lift),
                Stream.concat(spreadAlong.stream(), spreadAcross.stream()));
    }

    @Override
    public Set<HostCapability> requires() {
        return at == FxAnchor.TARGET ? Set.of(HostCapability.TARGET) : Set.of();
    }
}
