package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The per-tick trigger: finishes the first tick an entity passing every
 * filter stands within the volume around the host. The proximity mine is
 * {@code await_entity radius=3 where=[living]} followed by {@code explode}.
 *
 * @param shape  the volume shape
 * @param radius the volume radius in blocks, evaluated each tick
 * @param where  the filters an entity must pass; empty keeps any entity
 */
public record AwaitEntityStep(SelectionShape shape, Expr radius, List<EntityFilter> where) implements Step {

    private static final String NAME = "await_entity";
    private static final String FIELD_SHAPE = "shape";
    private static final String FIELD_RADIUS = "radius";
    private static final String FIELD_WHERE = "where";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<AwaitEntityStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            SelectionShape.CODEC.optionalFieldOf(FIELD_SHAPE, SelectionShape.SPHERE).forGetter(AwaitEntityStep::shape),
            Expr.CODEC.fieldOf(FIELD_RADIUS).forGetter(AwaitEntityStep::radius),
            EntityFilter.CODEC.listOf().optionalFieldOf(FIELD_WHERE, List.of()).forGetter(AwaitEntityStep::where)
    ).apply(inst, AwaitEntityStep::new));

    /**
     * The registered type.
     */
    public static final StepType<AwaitEntityStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<AwaitEntityStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        return context.hostAs(EntityScanHost.class).anyEntityWithin(shape, radius.evaluate(context), Set.copyOf(where));
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(radius);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TICKING, HostCapability.ENTITY_SCAN);
    }
}
