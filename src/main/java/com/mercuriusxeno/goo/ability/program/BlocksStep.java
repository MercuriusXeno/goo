package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * A stream's block pass: the child steps run, each tick of the hold, on a
 * {@link HostKind#STREAMED_BLOCK} host for every block the stream holds,
 * while the rest of the program runs on each entity it holds. Only a stream
 * delivery runs the pass; ticked on any other host it does nothing.
 * decision unmake-waves-dissolve-by-crucible-cost
 *
 * @param steps the steps run on each held block
 */
public record BlocksStep(List<Step> steps) implements Step {

    private static final String NAME = "blocks";
    private static final String FIELD_STEPS = "steps";

    /**
     * Codec for the step's params. The child list codec is read lazily
     * because {@link StepTypes} registers this type while building it.
     */
    public static final MapCodec<BlocksStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.lazyInitialized(() -> StepTypes.LIST_CODEC).fieldOf(FIELD_STEPS).forGetter(BlocksStep::steps)
    ).apply(inst, BlocksStep::new));

    /**
     * The registered type.
     */
    public static final StepType<BlocksStep> TYPE = new StepType<>(NAME, CODEC);

    /**
     * The block pass's steps in a program: the bodies of its block steps, in order.
     *
     * @param program the ability's program
     * @return the steps run on each held block, empty for a program with no pass
     */
    public static List<Step> passOf(List<Step> program) {
        return program.stream()
                .filter(BlocksStep.class::isInstance)
                .flatMap(step -> ((BlocksStep) step).steps().stream())
                .toList();
    }

    /**
     * A program with its block steps taken out: what runs on each held entity.
     *
     * @param program the ability's program
     * @return the program's other steps, in order
     */
    public static List<Step> withoutPass(List<Step> program) {
        return program.stream().filter(step -> !(step instanceof BlocksStep)).toList();
    }

    @Override
    public StepType<BlocksStep> type() {
        return TYPE;
    }

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
        return steps.stream().map(child -> new HostedStep(child, HostKind.STREAMED_BLOCK));
    }
}
