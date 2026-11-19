package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.nether.Invert;
import com.mojang.serialization.MapCodec;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Nether's Invert, laid on the player holding its held effect: the player's
 * gravity turns over and the ceiling is its ground until the held effect
 * ends, by the player's press, by running dry, or by the player meeting open
 * sky. Invert is {@code invert}.
 * invert-flips-your-gravity-and-lets-you-walk-on-ceilings
 */
public record InvertStep() implements Step {

    private static final String NAME = "invert";

    /** Codec for the step, which takes no params. */
    public static final MapCodec<InvertStep> CODEC = MapCodec.unit(InvertStep::new);

    /** The registered type. */
    public static final StepType<InvertStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<InvertStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        Invert.lay(context.hostAs(TargetHost.class).target());
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
