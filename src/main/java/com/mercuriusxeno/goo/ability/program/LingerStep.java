package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Stands the ability's own block where its blob lands and hands it the child
 * steps, which run on the block's host from that tick until they end, when
 * the block goes (decision lingering-abilities-place-their-own-thing). An
 * ability that lingers (the crystal cloud, the metal trap, the black hole,
 * the lurker) names this step; one whose program ends the tick it
 * lands names none and leaves only its effect behind.
 *
 * @param steps the steps the ability's block runs
 */
public record LingerStep(List<Step> steps) implements Step {

    private static final String NAME = "linger";
    private static final String FIELD_STEPS = "steps";

    /**
     * Codec for the step's params. The child list codec is read lazily
     * because {@link StepTypes} registers this type while building it.
     */
    public static final MapCodec<LingerStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.lazyInitialized(() -> StepTypes.LIST_CODEC).fieldOf(FIELD_STEPS).forGetter(LingerStep::steps)
    ).apply(inst, LingerStep::new));

    /**
     * The registered type.
     */
    public static final StepType<LingerStep> TYPE = new StepType<>(NAME, CODEC);

    /**
     * Finds the steps an ability's block runs: the body of the first linger
     * step in its program, at any depth.
     *
     * @param program the ability's program
     * @return the block's steps, or empty for an ability that never lingers
     */
    public static Optional<List<Step>> bodyOf(List<Step> program) {
        return program.stream().flatMap(LingerStep::withDescendants)
                .filter(LingerStep.class::isInstance)
                .map(step -> ((LingerStep) step).steps())
                .findFirst();
    }

    private static Stream<Step> withDescendants(Step step) {
        return Stream.concat(Stream.of(step), step.children().flatMap(LingerStep::withDescendants));
    }

    @Override
    public StepType<LingerStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        context.hostAs(LingerHost.class).linger(steps);
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.empty();
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.LINGER);
    }

    @Override
    public Stream<Step> children() {
        return steps.stream();
    }

    @Override
    public Stream<HostedStep> hostedChildren(HostKind host) {
        return steps.stream().map(child -> new HostedStep(child, HostKind.MARKER));
    }
}
