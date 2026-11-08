package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Explodes at the host's anchor and finishes. The power is an expression
 * over the host, one value for a single throw; a written reach caps the
 * sphere the explosion cuts apart from its power, which otherwise sets it.
 *
 * @param power the explosion power, evaluated when the step runs
 * @param reach the radius the explosion cuts at most, empty for the power's own open-air reach
 * @param mode  how blocks are treated
 */
public record ExplodeStep(Expr power, Optional<Expr> reach, ExplosionMode mode) implements Step {

    private static final String NAME = "explode";
    private static final String FIELD_POWER = "power";
    private static final String FIELD_REACH = "reach";
    private static final String FIELD_INTERACTION = "interaction";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<ExplodeStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_POWER).forGetter(ExplodeStep::power),
            Expr.CODEC.optionalFieldOf(FIELD_REACH).forGetter(ExplodeStep::reach),
            ExplosionMode.CODEC.optionalFieldOf(FIELD_INTERACTION, ExplosionMode.TNT).forGetter(ExplodeStep::mode)
    ).apply(inst, ExplodeStep::new));

    /**
     * The registered type.
     */
    public static final StepType<ExplodeStep> TYPE = new StepType<>(NAME, CODEC);

    /**
     * An explosion whose power sets its reach.
     *
     * @param power the explosion power
     * @param mode  how blocks are treated
     */
    public ExplodeStep(Expr power, ExplosionMode mode) {
        this(power, Optional.empty(), mode);
    }

    /**
     * The radius the explosion cuts at most under these variables: the
     * written reach, or the open-air reach of its power.
     * blast-fills-its-drawn-radius
     *
     * @param variables the host's variables
     * @return the reach in blocks
     */
    public double reachAt(Variables variables) {
        return reach.map(written -> written.evaluate(variables))
                .orElseGet(() -> ExplosionMarch.maxReach(power.evaluateFloat(variables)));
    }

    @Override
    public StepType<ExplodeStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        context.hostAs(ExplodeHost.class).explode(power.evaluateFloat(context), reachAt(context), mode);
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.concat(Stream.of(power), reach.stream());
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.EXPLODE);
    }
}
