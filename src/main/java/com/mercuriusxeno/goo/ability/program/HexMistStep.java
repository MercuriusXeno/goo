package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Names the hex mist as a stream's look: while the stream is held, with or
 * without a mob in its cone, the streaming client fills the flat cone through
 * the glove hand with a rough hex-purple mist drifting toward the hand. The
 * server does nothing for it, as for {@link WindStep}: {@code hex_mist}.
 * drain-casts-a-hex-mist-cone-toward-the-hand
 */
public record HexMistStep() implements TakesNoEntity {

    private static final String NAME = "hex_mist";

    /**
     * Codec for the step, which takes no params.
     */
    public static final MapCodec<HexMistStep> CODEC = MapCodec.unit(new HexMistStep());

    /**
     * The registered type.
     */
    public static final StepType<HexMistStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<HexMistStep> type() {
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
