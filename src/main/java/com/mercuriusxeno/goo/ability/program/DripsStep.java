package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Counts a tap's drips onto the block below and runs its child steps once
 * the count the JSON names has gathered, starting the count over. A frost
 * tap pulses its nova this way:
 * {@code drips count=6 steps=[nova ..., freeze_blocks ...]}
 * (decision nova-drip-pulses-a-short-lasting-freeze).
 *
 * @param count the drips that gather before the steps run
 * @param steps the steps run on the tap's landing once the count gathers
 */
public record DripsStep(int count, List<Step> steps) implements Step {

    private static final String NAME = "drips";
    private static final String FIELD_COUNT = "count";
    private static final String FIELD_STEPS = "steps";

    /**
     * Codec for the step's params, the child list read lazily since
     * {@link StepTypes} registers this type while building it.
     */
    public static final MapCodec<DripsStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.INT.fieldOf(FIELD_COUNT).forGetter(DripsStep::count),
            Codec.lazyInitialized(() -> StepTypes.LIST_CODEC).fieldOf(FIELD_STEPS).forGetter(DripsStep::steps)
    ).apply(inst, DripsStep::new));

    /**
     * The registered type.
     */
    public static final StepType<DripsStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<DripsStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        DripHost host = context.hostAs(DripHost.class);
        if (host.countDrip() >= count) {
            host.resetDrips();
            new ProgramBehavior(steps).tick(host);
        }
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.empty();
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.DRIP);
    }

    @Override
    public Stream<Step> children() {
        return steps.stream();
    }
}
