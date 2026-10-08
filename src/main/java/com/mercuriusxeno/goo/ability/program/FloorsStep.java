package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Runs the child steps on each open floor within a radius of the host's
 * anchor, each on a sprayed-floor host whose {@code distance} reads how far
 * that floor's open cell sits from the anchor, then finishes. Colonize buds
 * the landing for certain and the floors around it by chance:
 * {@code floors radius=2 steps=[branch when="at_least(1, distance) ..."]}
 * (decision colonize-blob-grows-the-network).
 *
 * @param radius the reach in blocks, evaluated when the step runs
 * @param steps  the child steps run once per floor
 */
public record FloorsStep(Expr radius, List<Step> steps) implements Step {

    private static final String NAME = "floors";
    private static final String FIELD_RADIUS = "radius";
    private static final String FIELD_STEPS = "steps";

    /**
     * Codec for the step's params. The child list codec is read lazily
     * because {@link StepTypes} registers this type while building it.
     */
    public static final MapCodec<FloorsStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_RADIUS).forGetter(FloorsStep::radius),
            Codec.lazyInitialized(() -> StepTypes.LIST_CODEC).fieldOf(FIELD_STEPS).forGetter(FloorsStep::steps)
    ).apply(inst, FloorsStep::new));

    /**
     * The registered type.
     */
    public static final StepType<FloorsStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<FloorsStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        context.hostAs(FloorScanHost.class).forEachFloorWithin(radius.evaluate(context),
                floor -> new ProgramBehavior(steps).tick(floor));
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(radius);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.FLOOR_SCAN);
    }

    @Override
    public Stream<Step> children() {
        return steps.stream();
    }

    @Override
    public Stream<HostedStep> hostedChildren(HostKind host) {
        return steps.stream().map(child -> new HostedStep(child, HostKind.SURFACE));
    }
}
