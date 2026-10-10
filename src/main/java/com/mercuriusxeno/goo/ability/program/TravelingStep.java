package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * What a thrown blob does while it flies: its child steps run on each tick
 * of the flight around the point the blob has reached, and nothing at the
 * landing, where the rest of the program runs. Every client watching draws
 * a swirling frost nova of the swirl radius about the blob as it flies.
 * Frost's Orb freezes its path this way:
 * {@code traveling swirl=3 steps=[entities radius=3 [freeze ...], freeze_blocks radius=2]}
 * (decision orb-carries-a-swirling-nova).
 *
 * @param swirl the radius of the swirling nova drawn about the blob
 * @param steps the steps run each flight tick on the blob in flight
 */
public record TravelingStep(float swirl, List<Step> steps) implements Step {

    private static final String NAME = "traveling";
    private static final String FIELD_SWIRL = "swirl";
    private static final String FIELD_STEPS = "steps";

    /**
     * Codec for the step's params, the child list read lazily since
     * {@link StepTypes} registers this type while building it.
     */
    public static final MapCodec<TravelingStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.FLOAT.optionalFieldOf(FIELD_SWIRL, 0f).forGetter(TravelingStep::swirl),
            Codec.lazyInitialized(() -> StepTypes.LIST_CODEC).fieldOf(FIELD_STEPS).forGetter(TravelingStep::steps)
    ).apply(inst, TravelingStep::new));

    /**
     * The registered type.
     */
    public static final StepType<TravelingStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<TravelingStep> type() {
        return TYPE;
    }

    /**
     * The traveling step a program holds at its top, if any.
     *
     * @param behaviors the program
     * @return the first top-level traveling step, or empty
     */
    public static Optional<TravelingStep> of(List<Step> behaviors) {
        return behaviors.stream().filter(TravelingStep.class::isInstance).map(TravelingStep.class::cast).findFirst();
    }

    /** The landing runs nothing of the flight. */
    @Override
    public boolean tick(StepContext context) {
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.empty();
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of();
    }

    @Override
    public Stream<Step> children() {
        return steps.stream();
    }

    @Override
    public Stream<HostedStep> hostedChildren(HostKind host) {
        return steps.stream().map(child -> new HostedStep(child, HostKind.FLIGHT));
    }
}
