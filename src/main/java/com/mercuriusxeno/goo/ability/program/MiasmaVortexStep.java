package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Names the miasma vortex as a stream's look: while the stream is held, the
 * streaming client fills the cone it strikes, along the look from the glove
 * hand, with a turning, rifled vortex of miasma. The server does nothing for
 * it, as for {@link WindStep}: {@code miasma_vortex}.
 * drain-casts-a-filled-tornadic-miasma-vortex-along-the-look
 */
public record MiasmaVortexStep() implements TakesNoEntity {

    private static final String NAME = "miasma_vortex";

    /**
     * Codec for the step, which takes no params.
     */
    public static final MapCodec<MiasmaVortexStep> CODEC = MapCodec.unit(new MiasmaVortexStep());

    /**
     * The registered type.
     */
    public static final StepType<MiasmaVortexStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<MiasmaVortexStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.empty();
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of();
    }
}
