package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Selects the host's target when it passes every filter and runs the
 * child steps on it within the same tick, then finishes; a target a
 * filter rejects skips the children. The struck entity is the implicit
 * target of every effect step, so this container earns its place only
 * where a {@code where} filter is wanted: shroom debuff wraps its potion
 * steps in {@code target where=[not_boss]}.
 *
 * <p>The children run to completion in the tick the selection runs, so
 * they are instant steps; the hosts that provide a target provide no
 * tick driver, and a waiting child refuses at load on them.
 *
 * @param where the filters the target must pass; empty keeps any target
 * @param steps the child steps run on the selected target
 */
public record TargetStep(List<EntityFilter> where, List<Step> steps) implements Step {

    private static final String NAME = "target";
    private static final String FIELD_WHERE = "where";
    private static final String FIELD_STEPS = "steps";

    /**
     * Codec for the step's params. The child list codec is read lazily
     * because {@link StepTypes} registers this type while building it.
     */
    public static final MapCodec<TargetStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            EntityFilter.CODEC.listOf().optionalFieldOf(FIELD_WHERE, List.of()).forGetter(TargetStep::where),
            Codec.lazyInitialized(() -> StepTypes.LIST_CODEC).fieldOf(FIELD_STEPS).forGetter(TargetStep::steps)
    ).apply(inst, TargetStep::new));

    /**
     * The registered type.
     */
    public static final StepType<TargetStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<TargetStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        if (context.host().targetPasses(Set.copyOf(where))) {
            new ProgramBehavior(steps).tick(context.host());
        }
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

    @Override
    public Stream<Step> children() {
        return steps.stream();
    }
}
