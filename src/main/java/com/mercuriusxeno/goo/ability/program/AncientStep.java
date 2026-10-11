package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.held.Ancient;
import com.mojang.serialization.MapCodec;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Yore's Ancient, laid as its held effect starts: the target wears the aged
 * stone-and-gold overlay, and while the held effect stands no damage takes
 * it below half a heart. The effect list shows the one yore brew icon.
 * ancient-makes-the-player-immortal
 */
public record AncientStep() implements Step {

    private static final String NAME = "ancient";

    /**
     * Codec for the step, which takes no params.
     */
    public static final MapCodec<AncientStep> CODEC = MapCodec.unit(AncientStep::new);

    /**
     * The registered type.
     */
    public static final StepType<AncientStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<AncientStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        Ancient.lay(context.hostAs(TargetHost.class).target());
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
