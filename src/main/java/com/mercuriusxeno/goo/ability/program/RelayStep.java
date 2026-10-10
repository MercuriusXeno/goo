package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Relay's combo step, running for as long as the prism stands: each tick
 * the prism gives the strongest redstone signal reaching any other relay
 * it links to through air, so a signal at one relay comes out at every
 * relay it reaches: {@code relay}.
 * relay-prism-carries-the-signal-through-air
 */
public record RelayStep() implements Step {

    private static final String NAME = "relay";

    /**
     * Codec for the step, which takes no params.
     */
    public static final MapCodec<RelayStep> CODEC = MapCodec.unit(RelayStep::new);

    /**
     * The registered type.
     */
    public static final StepType<RelayStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<RelayStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        context.hostAs(RelayHost.class).carrySignal();
        return false;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.empty();
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TICKING, HostCapability.RELAY);
    }
}
