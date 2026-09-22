package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Selects every living entity within the volume around the host anchor
 * that passes every filter, binds each in turn as the target and runs the
 * child steps on it within the same tick, then finishes. On the struck
 * entity host the anchor is the target, so blaze ignite's splash is
 * {@code entities shape=sphere radius=2.5 where=[living, not_fire_immune]}
 * around the entity the blob hit.
 *
 * <p>The children run to completion in the tick the selection runs, so
 * they are instant steps, and they act on a rebound target, so the step
 * needs a host that provides one.
 *
 * @param shape  the volume shape
 * @param radius the volume radius in blocks, evaluated when the step runs
 * @param where  the filters an entity must pass; empty keeps any living entity
 * @param steps  the child steps run once per selected entity
 */
public record EntitiesStep(SelectionShape shape, Expr radius, List<EntityFilter> where,
                           List<Step> steps) implements Step {

    private static final String NAME = "entities";
    private static final String FIELD_SHAPE = "shape";
    private static final String FIELD_RADIUS = "radius";
    private static final String FIELD_WHERE = "where";
    private static final String FIELD_STEPS = "steps";

    /**
     * Codec for the step's params. The child list codec is read lazily
     * because {@link StepTypes} registers this type while building it.
     */
    public static final MapCodec<EntitiesStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            SelectionShape.CODEC.optionalFieldOf(FIELD_SHAPE, SelectionShape.SPHERE).forGetter(EntitiesStep::shape),
            Expr.CODEC.fieldOf(FIELD_RADIUS).forGetter(EntitiesStep::radius),
            EntityFilter.CODEC.listOf().optionalFieldOf(FIELD_WHERE, List.of()).forGetter(EntitiesStep::where),
            Codec.lazyInitialized(() -> StepTypes.LIST_CODEC).fieldOf(FIELD_STEPS).forGetter(EntitiesStep::steps)
    ).apply(inst, EntitiesStep::new));

    /**
     * The registered type.
     */
    public static final StepType<EntitiesStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<EntitiesStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        context.host().forEachEntityWithin(shape, radius.evaluate(context), Set.copyOf(where),
                selected -> new ProgramBehavior(steps).tick(selected));
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(radius);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.ENTITY_SCAN, HostCapability.TARGET);
    }

    @Override
    public Stream<Step> children() {
        return steps.stream();
    }
}
