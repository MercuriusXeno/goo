package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Churns the column under the landed block, one turn a tick, until it has
 * turned once per layer: then what stood in the core stands in the strips,
 * upside down, and what stood in the strips stands in the core, so the
 * bottom has come up and the top gone down. Churn is {@code churn depth=4*size},
 * the depth the player's drag picks.
 * decision churn-rotates-a-plus-shaped-column
 *
 * @param depth the column's layers, evaluated each tick
 */
public record ChurnStep(Expr depth) implements Step {

    private static final String NAME = "churn";
    private static final String FIELD_DEPTH = "depth";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<ChurnStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_DEPTH).forGetter(ChurnStep::depth)
    ).apply(inst, ChurnStep::new));

    /**
     * The registered type.
     */
    public static final StepType<ChurnStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<ChurnStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        int layers = Math.max(1, depth.evaluateInt(context));
        context.hostAs(ChurnHost.class).churnColumn(layers);
        return context.stepTicks() + 1 >= layers;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(depth);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.CHURN);
    }
}
