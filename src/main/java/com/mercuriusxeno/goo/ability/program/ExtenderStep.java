package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Extender's step: it marks the held effect as the extender, so its pulse
 * upkeep covers alternate ticks of every other held effect's; the step itself
 * acts on nothing: {@code extender}.
 * extender-multiplies-the-next-self-duration
 */
public record ExtenderStep() implements Step {

    private static final String NAME = "extender";

    /**
     * Codec for the step, which takes no params.
     */
    public static final MapCodec<ExtenderStep> CODEC = MapCodec.unit(ExtenderStep::new);

    /**
     * The registered type.
     */
    public static final StepType<ExtenderStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<ExtenderStep> type() {
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
