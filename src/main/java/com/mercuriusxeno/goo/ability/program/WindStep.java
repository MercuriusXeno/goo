package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Names line-drawn wind as a stream's look: while the stream is held, the
 * streaming client draws white and pale gray wind lines that rush straight
 * from the glove, then spiral up, down or out and fade, with snowflakes
 * flitting along them where the step asks for them. The server does nothing
 * for it, so Cold names it with snowflakes and a typhoon stream can name it
 * without: {@code wind snowflakes=true}. Where the step names an exhaust, the
 * lines leave from behind the player and rush backward against the look
 * instead, a jet's wake: {@code wind exhaust={range=4 cone=24}}.
 * cold-streams-wind-lines-and-snowflakes
 * jet-pushes-along-the-look-while-held
 *
 * @param snowflakes whether snowflakes flit along the lines
 * @param exhaust    the wake the lines blow behind the player, or empty to blow them from the glove
 */
public record WindStep(boolean snowflakes, Optional<Exhaust> exhaust) implements Step {

    private static final String NAME = "wind";
    private static final String FIELD_SNOWFLAKES = "snowflakes";
    private static final String FIELD_EXHAUST = "exhaust";

    /**
     * A jet's wake: the cone behind the player the lines blow back through.
     *
     * @param range       how far back the lines rush, in blocks
     * @param coneDegrees the cone's apex angle, in degrees
     */
    public record Exhaust(double range, double coneDegrees) {

        private static final String FIELD_RANGE = "range";
        private static final String FIELD_CONE = "cone";

        /** Codec for the wake. */
        public static final Codec<Exhaust> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.DOUBLE.fieldOf(FIELD_RANGE).forGetter(Exhaust::range),
                Codec.DOUBLE.fieldOf(FIELD_CONE).forGetter(Exhaust::coneDegrees)
        ).apply(inst, Exhaust::new));
    }

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<WindStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.BOOL.optionalFieldOf(FIELD_SNOWFLAKES, false).forGetter(WindStep::snowflakes),
            Exhaust.CODEC.optionalFieldOf(FIELD_EXHAUST).forGetter(WindStep::exhaust)
    ).apply(inst, WindStep::new));

    /**
     * The registered type.
     */
    public static final StepType<WindStep> TYPE = new StepType<>(NAME, CODEC);

    /**
     * Wind blown from the glove.
     *
     * @param snowflakes whether snowflakes flit along the lines
     */
    public WindStep(boolean snowflakes) {
        this(snowflakes, Optional.empty());
    }

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
