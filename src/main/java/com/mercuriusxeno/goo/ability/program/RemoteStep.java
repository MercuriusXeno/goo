package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.held.Remote;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Kinetic's Remote, laid on the player holding its held
 * effect on the glove: the player's block and entity interaction ranges rise by the
 * reach until the held effect ends.
 * decision telekinesis-enacts-at-extended-reach
 *
 * @param reach the blocks added to each interaction range
 */
public record RemoteStep(double reach) implements Step {

    private static final String NAME = "remote";
    private static final String FIELD_REACH = "reach";

    /** Codec for the step's params. */
    public static final MapCodec<RemoteStep> CODEC =
            Codec.DOUBLE.fieldOf(FIELD_REACH).xmap(RemoteStep::new, RemoteStep::reach);

    /** The registered type. */
    public static final StepType<RemoteStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<RemoteStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        Remote.lay(context.hostAs(TargetHost.class).target(), reach);
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
