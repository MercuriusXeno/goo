package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.held.Telekinesis;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Kinetic's Telekinesis, laid on the player holding its self or brew
 * effect: the player's block and entity interaction ranges rise by the
 * reach until the held effect ends.
 * decision telekinesis-enacts-at-extended-reach
 *
 * @param reach the blocks added to each interaction range
 */
public record TelekinesisStep(double reach) implements Step {

    private static final String NAME = "telekinesis";
    private static final String FIELD_REACH = "reach";

    /** Codec for the step's params. */
    public static final MapCodec<TelekinesisStep> CODEC =
            Codec.DOUBLE.fieldOf(FIELD_REACH).xmap(TelekinesisStep::new, TelekinesisStep::reach);

    /** The registered type. */
    public static final StepType<TelekinesisStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<TelekinesisStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        Telekinesis.lay(context.hostAs(TargetHost.class).target(), reach);
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
