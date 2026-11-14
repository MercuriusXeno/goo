package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Extend's step: it marks the held effect as the Extend, so its pulse
 * upkeep covers alternate ticks of every other held effect's; the step itself
 * acts on nothing: {@code extend}.
 * extender-multiplies-the-next-self-duration
 */
public record ExtendStep() implements Step {

    private static final String NAME = "extend";

    /**
     * Codec for the step, which takes no params.
     */
    public static final MapCodec<ExtendStep> CODEC = MapCodec.unit(ExtendStep::new);

    /**
     * The registered type.
     */
    public static final StepType<ExtendStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<ExtendStep> type() {
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
