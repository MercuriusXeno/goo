package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Removes the host's target from the world without a death, drops or a
 * loot roll, and finishes; aeon's ritual discards the mob it turned into
 * its spawn egg (decision aeon-mob-ritual-drops-spawn-egg).
 */
public record DiscardStep() implements Step {

    private static final String NAME = "discard";

    /**
     * Codec for the step, which takes no params.
     */
    public static final MapCodec<DiscardStep> CODEC = MapCodec.unit(DiscardStep::new);

    /**
     * The registered type.
     */
    public static final StepType<DiscardStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<DiscardStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        context.host().target().discard();
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.empty();
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TARGET);
    }
}
