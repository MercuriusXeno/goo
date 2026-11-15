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
 * @param power   the explosion power, evaluated when the step runs
 * @param reach   the radius the explosion cuts at most, empty for the power's own open-air reach
 * @param gathers when the explosion gathers its mined drops into one sphere, true when nonzero; empty for never
 * @param mode    how blocks are treated
 * @param look    how the explosion shows, the silent look unless written
 */
public record ExplodeStep(Expr power, Optional<Expr> reach, Optional<Expr> gathers, ExplosionMode mode,
                          ExplosionLook look) implements Step {

    private static final String NAME = "explode";
    private static final String FIELD_POWER = "power";
    private static final String FIELD_REACH = "reach";
    private static final String FIELD_GATHERS = "gathers";
    private static final String FIELD_INTERACTION = "interaction";
    private static final String FIELD_LOOK = "look";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<ExplodeStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_POWER).forGetter(ExplodeStep::power),
            Expr.CODEC.optionalFieldOf(FIELD_REACH).forGetter(ExplodeStep::reach),
            Expr.CODEC.optionalFieldOf(FIELD_GATHERS).forGetter(ExplodeStep::gathers),
            ExplosionMode.CODEC.optionalFieldOf(FIELD_INTERACTION, ExplosionMode.TNT).forGetter(ExplodeStep::mode),
            // blast-alone-takes-the-new-explosion-look
            ExplosionLook.CODEC.optionalFieldOf(FIELD_LOOK, ExplosionLook.SILENT).forGetter(ExplodeStep::look)
    ).apply(inst, ExplodeStep::new));

    /**
     * The registered type.
     */
    public static final StepType<ExplodeStep> TYPE = new StepType<>(NAME, CODEC);

    /**
     * A silent explosion whose power sets its reach and whose drops pop in place.
     *
     * @param power the explosion power
     * @param mode  how blocks are treated
     */
    public ExplodeStep(Expr power, ExplosionMode mode) {
        this(power, Optional.empty(), Optional.empty(), mode, ExplosionLook.SILENT);
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

    /**
     * Whether the explosion gathers its mined drops into one compression
     * sphere at its center under these variables.
     * large-blast-gathers-its-mined-drops-into-a-green-cored-sphere
     *
     * @param variables the host's variables
     * @return true when the written expression reads nonzero
     */
    public boolean gathersAt(Variables variables) {
        return gathers.map(written -> written.evaluate(variables) != 0).orElse(false);
    }

    @Override
    public StepType<ExplodeStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        context.hostAs(ExplodeHost.class).explode(power.evaluateFloat(context), reachAt(context), gathersAt(context),
                mode, look);
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.concat(Stream.concat(Stream.of(power), reach.stream()), gathers.stream());
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.EXPLODE);
    }
}
