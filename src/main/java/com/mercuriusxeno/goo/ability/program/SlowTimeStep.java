package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Aeon's chronosphere: a veil around the host's anchor that grows from the
 * impact point to its radius over the expand ticks and, every tick until its
 * duration runs out, slows every non-player living entity and every
 * projectile inside nearly to a halt, leaving players alone. The radius is
 * an expression, {@code size} for the radius the cast was dragged to.
 * chronosphere-hastes-players-slows-mobs
 *
 * @param radius      the veil's full radius in blocks
 * @param expandTicks the ticks the veil takes to grow to its radius
 * @param duration    the ticks the veil stands
 * @param slow        the share of its motion a slowed thing keeps a tick, 0 to 1
 */
public record SlowTimeStep(Expr radius, int expandTicks, int duration, double slow) implements Step {

    private static final String NAME = "slow_time";
    private static final String FIELD_RADIUS = "radius";
    private static final String FIELD_EXPAND_TICKS = "expand_ticks";
    private static final String FIELD_DURATION = "duration";
    private static final String FIELD_SLOW = "slow";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<SlowTimeStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_RADIUS).forGetter(SlowTimeStep::radius),
            Codec.INT.fieldOf(FIELD_EXPAND_TICKS).forGetter(SlowTimeStep::expandTicks),
            Codec.INT.fieldOf(FIELD_DURATION).forGetter(SlowTimeStep::duration),
            Codec.doubleRange(0, 1).fieldOf(FIELD_SLOW).forGetter(SlowTimeStep::slow)
    ).apply(inst, SlowTimeStep::new));

    /**
     * The registered type.
     */
    public static final StepType<SlowTimeStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<SlowTimeStep> type() {
        return TYPE;
    }

    /**
     * The veil's radius a number of ticks after it stood: growing evenly from
     * nothing to the full radius over the expand ticks.
     *
     * @param fullRadius the radius the veil grows to, its radius expression's value
     * @param ticks      ticks since the veil stood
     * @return the radius in blocks
     */
    public double radiusAt(double fullRadius, double ticks) {
        return expandTicks <= 0 ? fullRadius : fullRadius * Math.clamp(ticks / expandTicks, 0.0, 1.0);
    }

    @Override
    public boolean tick(StepContext context) {
        int ticks = context.stepTicks();
        context.hostAs(TimeVeilHost.class).slowWithin(radiusAt(radius.evaluate(context), ticks), slow);
        return ticks >= duration;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(radius);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TIME_VEIL, HostCapability.TICKING);
    }
}
