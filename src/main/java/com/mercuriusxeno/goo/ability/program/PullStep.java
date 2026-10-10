package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Pulls every living entity within a sphere around the host anchor toward
 * the anchor's center and finishes; the push goes through the entity's
 * knockback resistance. With {@code items} set it also draws in item
 * entities, taking each that reaches the anchor into the host's hoard. The
 * nether black hole pulls from three times its blast radius each tick it
 * expands and holds: {@code pull radius=9 speed=0.15 items=true}.
 *
 * @param radius the sphere radius in blocks, evaluated when the step runs
 * @param speed  the velocity added toward the center, in blocks per tick
 * @param items  true to draw item entities into the host's hoard as well
 */
public record PullStep(Expr radius, Expr speed, boolean items) implements Step {

    private static final String NAME = "pull";
    private static final String FIELD_RADIUS = "radius";
    private static final String FIELD_SPEED = "speed";
    private static final String FIELD_ITEMS = "items";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<PullStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_RADIUS).forGetter(PullStep::radius),
            Expr.CODEC.fieldOf(FIELD_SPEED).forGetter(PullStep::speed),
            Codec.BOOL.optionalFieldOf(FIELD_ITEMS, false).forGetter(PullStep::items)
    ).apply(inst, PullStep::new));

    /**
     * The registered type.
     */
    public static final StepType<PullStep> TYPE = new StepType<>(NAME, CODEC);

    /**
     * A pull of living entities alone.
     *
     * @param radius the sphere radius in blocks
     * @param speed  the velocity added toward the center
     */
    public PullStep(Expr radius, Expr speed) {
        this(radius, speed, false);
    }

    @Override
    public StepType<PullStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        double reach = radius.evaluate(context);
        double pace = speed.evaluate(context);
        context.hostAs(EntityScanHost.class).pullEntitiesWithin(reach, pace);
        if (items) {
            context.hostAs(HoardHost.class).pullItemsIntoHoard(reach, pace);
        }
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(radius, speed);
    }

    @Override
    public Set<HostCapability> requires() {
        return items ? Set.of(HostCapability.ENTITY_SCAN, HostCapability.HOARD) : Set.of(HostCapability.ENTITY_SCAN);
    }
}
