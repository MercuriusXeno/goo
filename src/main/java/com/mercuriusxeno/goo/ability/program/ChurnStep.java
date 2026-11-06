package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Pulls the vein under the landed block up a layer a tick, the ground above
 * it sinking into its place, and ends the tick no vein cell rises. Churn is
 * {@code churn depth=4*size}, the depth the player's drag picks.
 * decision churn-pulls-a-vein-up-as-a-fluid-chain
 *
 * @param depth how far down the column the churn reaches for a mineral, evaluated each tick
 */
public record ChurnStep(Expr depth) implements Step {

    private static final String NAME = "churn";
    private static final String FIELD_DEPTH = "depth";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<ChurnStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_DEPTH).forGetter(ChurnStep::depth)
    ).apply(inst, ChurnStep::new));

    /**
     * The registered type.
     */
    public static final StepType<ChurnStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<ChurnStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        int reach = Math.max(1, depth.evaluateInt(context));
        return !context.hostAs(ChurnHost.class).pullVein(reach);
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(depth);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.CHURN);
    }
}
