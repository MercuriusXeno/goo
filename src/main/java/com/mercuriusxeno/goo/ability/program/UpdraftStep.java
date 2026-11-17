package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Updraft's body step: each tick for the duration, a column of air standing
 * above the host carries every entity inside it upward; then the step ends
 * and the lingering blob goes with it. The streaming client draws frost's
 * wind lines rising through the column, which it sizes from this step:
 * {@code updraft radius=1 height=8 speed=0.4 duration=200}.
 * updraft-blob-stands-a-column-of-wind
 *
 * @param radius   the column's half width in blocks, evaluated when the step runs
 * @param height   the column's height in blocks, evaluated when the step runs
 * @param speed    the rise it carries at, in blocks per tick, evaluated when the step runs
 * @param duration the ticks the column stands, evaluated when the step runs
 */
public record UpdraftStep(Expr radius, Expr height, Expr speed, Expr duration) implements Step {

    private static final String NAME = "updraft";
    private static final String FIELD_RADIUS = "radius";
    private static final String FIELD_HEIGHT = "height";
    private static final String FIELD_SPEED = "speed";
    private static final String FIELD_DURATION = "duration";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<UpdraftStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_RADIUS).forGetter(UpdraftStep::radius),
            Expr.CODEC.fieldOf(FIELD_HEIGHT).forGetter(UpdraftStep::height),
            Expr.CODEC.fieldOf(FIELD_SPEED).forGetter(UpdraftStep::speed),
            Expr.CODEC.fieldOf(FIELD_DURATION).forGetter(UpdraftStep::duration)
    ).apply(inst, UpdraftStep::new));

    /**
     * The registered type.
     */
    public static final StepType<UpdraftStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<UpdraftStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        if (context.stepTicks() >= duration.evaluateInt(context)) {
            return true;
        }
        context.hostAs(EntityScanHost.class).liftEntitiesInColumn(radius.evaluate(context), height.evaluate(context),
                speed.evaluate(context));
        return false;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(radius, height, speed, duration);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TICKING, HostCapability.ENTITY_SCAN);
    }
}
