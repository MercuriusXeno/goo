package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Watches a sphere around the host each tick, pulsing to its viewers with
 * the nearest kept entity's distance, and finishes the tick that entity
 * stands within {@code until}. The lurker is {@code watch} then
 * {@code await_entity} then {@code explode}.
 * decision lurker-blob-brightens-then-detonates
 *
 * @param radius the radius watched, in blocks, evaluated each tick
 * @param until  the distance at which the watch ends, in blocks, evaluated each tick
 * @param where  the filters an entity must pass; empty keeps any entity
 */
public record WatchStep(Expr radius, Expr until, List<EntityFilter> where) implements Step {

    private static final String NAME = "watch";
    private static final String FIELD_RADIUS = "radius";
    private static final String FIELD_UNTIL = "until";
    private static final String FIELD_WHERE = "where";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<WatchStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_RADIUS).forGetter(WatchStep::radius),
            Expr.CODEC.fieldOf(FIELD_UNTIL).forGetter(WatchStep::until),
            EntityFilter.CODEC.listOf().optionalFieldOf(FIELD_WHERE, List.of()).forGetter(WatchStep::where)
    ).apply(inst, WatchStep::new));

    /**
     * The registered type.
     */
    public static final StepType<WatchStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<WatchStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        WatchHost host = context.hostAs(WatchHost.class);
        double watched = radius.evaluate(context);
        OptionalDouble nearest = host.nearestEntityDistance(watched, Set.copyOf(where));
        if (nearest.isEmpty()) {
            return false;
        }
        host.pulse(nearest.getAsDouble(), watched);
        return nearest.getAsDouble() <= until.evaluate(context);
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(radius, until);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TICKING, HostCapability.WATCH);
    }
}
