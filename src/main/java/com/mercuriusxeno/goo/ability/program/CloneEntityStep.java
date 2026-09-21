package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Spawns a fresh entity of the host's target's type beside it on a roll
 * and finishes; the host rolls, so the chance is what the step hands
 * over. Vital clone is {@code clone_entity chance="100 / pow(max_health, 0.6)"}.
 *
 * @param chance the percent chance of a clone, evaluated when the step runs
 */
public record CloneEntityStep(Expr chance) implements Step {

    private static final String NAME = "clone_entity";
    private static final String FIELD_CHANCE = "chance";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<CloneEntityStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_CHANCE).forGetter(CloneEntityStep::chance)
    ).apply(inst, CloneEntityStep::new));

    /**
     * The registered type.
     */
    public static final StepType<CloneEntityStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<CloneEntityStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        context.host().cloneTarget(chance.evaluateFloat(context));
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(chance);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TARGET);
    }
}
