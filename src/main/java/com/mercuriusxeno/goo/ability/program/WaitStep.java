package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.stream.Stream;

/**
 * Idles for a number of ticks, then finishes. {@code wait ticks=2} lets two
 * ticks pass before the next step runs.
 *
 * @param ticks how many ticks to idle, evaluated on the step's first tick
 */
public record WaitStep(Expr ticks) implements Step {

    private static final String NAME = "wait";
    private static final String FIELD_TICKS = "ticks";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<WaitStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_TICKS).forGetter(WaitStep::ticks)
    ).apply(inst, WaitStep::new));

    /**
     * The registered type.
     */
    public static final StepType<WaitStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<WaitStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        return context.stepTicks() >= ticks.evaluateInt(context);
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(ticks);
    }
}
