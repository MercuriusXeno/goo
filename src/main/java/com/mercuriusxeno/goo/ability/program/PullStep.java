package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Pulls every living entity within a sphere around the host anchor toward
 * the anchor's center and finishes; the push goes through the entity's
 * knockback resistance. The nether black hole pulls from three times its
 * blast radius each tick it expands and holds:
 * {@code pull radius="3 * (1 + 2 * stacks)" speed=0.15}.
 *
 * @param radius the sphere radius in blocks, evaluated when the step runs
 * @param speed  the velocity added toward the center, in blocks per tick
 */
public record PullStep(Expr radius, Expr speed) implements Step {

    private static final String NAME = "pull";
    private static final String FIELD_RADIUS = "radius";
    private static final String FIELD_SPEED = "speed";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<PullStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_RADIUS).forGetter(PullStep::radius),
            Expr.CODEC.fieldOf(FIELD_SPEED).forGetter(PullStep::speed)
    ).apply(inst, PullStep::new));

    /**
     * The registered type.
     */
    public static final StepType<PullStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<PullStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        context.host().pullEntitiesWithin(radius.evaluate(context), speed.evaluate(context));
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(radius, speed);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.ENTITY_SCAN);
    }
}
