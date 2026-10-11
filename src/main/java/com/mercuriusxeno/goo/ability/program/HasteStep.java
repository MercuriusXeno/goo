package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.held.Haste;
import com.mojang.serialization.MapCodec;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Aeon's Haste, laid as its held effect starts: the target wears the golden
 * haste overlay while the held effect stands. The speed and haste
 * themselves ride the aeon brew effect the held effect shows, so the effect
 * list carries the one aeon brew icon.
 * haste-stacks-speed-under-the-golden-overlay
 */
public record HasteStep() implements Step {

    private static final String NAME = "haste";

    /**
     * Codec for the step, which takes no params.
     */
    public static final MapCodec<HasteStep> CODEC = MapCodec.unit(HasteStep::new);

    /**
     * The registered type.
     */
    public static final StepType<HasteStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<HasteStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        Haste.lay(context.hostAs(TargetHost.class).target());
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
