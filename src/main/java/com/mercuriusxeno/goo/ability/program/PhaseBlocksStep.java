package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Opens a portable hole at the landing and finishes: a square tunnel from the
 * struck block inward to the depth goes out of phase, and each cell steps back
 * after the lifetime. Quantum's hole is {@code phase_blocks depth=3 radius=1 lifetime=200},
 * three cells deep and three across.
 * portable-hole-phases-blocks-for-a-while
 *
 * @param depth    how many cells the hole runs, evaluated when the step runs
 * @param radius   how many cells the square reaches out from the line, evaluated when the step runs
 * @param lifetime the ticks before each cell steps back, evaluated when the step runs
 */
public record PhaseBlocksStep(Expr depth, Expr radius, Expr lifetime) implements Step {

    private static final String NAME = "phase_blocks";
    private static final String FIELD_DEPTH = "depth";
    private static final String FIELD_RADIUS = "radius";
    private static final String FIELD_LIFETIME = "lifetime";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<PhaseBlocksStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_DEPTH).forGetter(PhaseBlocksStep::depth),
            Expr.CODEC.fieldOf(FIELD_RADIUS).forGetter(PhaseBlocksStep::radius),
            Expr.CODEC.fieldOf(FIELD_LIFETIME).forGetter(PhaseBlocksStep::lifetime)
    ).apply(inst, PhaseBlocksStep::new));

    /**
     * The registered type.
     */
    public static final StepType<PhaseBlocksStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<PhaseBlocksStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        context.hostAs(PhaseBlocksHost.class).phaseBlocks(depth.evaluateInt(context), radius.evaluateInt(context),
                lifetime.evaluateInt(context));
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(depth, radius, lifetime);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.PHASE_BLOCKS);
    }
}
