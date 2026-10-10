package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Names line-drawn wind as a stream's look: while the stream is held, the
 * streaming client draws white and pale gray wind lines that rush straight
 * from the glove, then spiral up, down or out and fade, with snowflakes
 * flitting along them where the step asks for them. The server does nothing
 * for it, so Cold names it with snowflakes and a typhoon stream can name it
 * without: {@code wind snowflakes=true}.
 * cold-streams-wind-lines-and-snowflakes
 *
 * @param snowflakes whether snowflakes flit along the lines
 */
public record WindStep(boolean snowflakes) implements Step {

    private static final String NAME = "wind";
    private static final String FIELD_SNOWFLAKES = "snowflakes";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<WindStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.BOOL.optionalFieldOf(FIELD_SNOWFLAKES, false).forGetter(WindStep::snowflakes)
    ).apply(inst, WindStep::new));

    /**
     * The registered type.
     */
    public static final StepType<WindStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<WindStep> type() {
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
