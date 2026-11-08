package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import java.util.stream.Stream;

/**
 * A sculk call: the shriek blob pulses a ring out through the ground once
 * each period, the ring's radius growing from the blob to the reach over the
 * period, and every mob the ring crosses turns and walks to the blob; each
 * pulse calls again every mob in reach, so the pull holds while the blob
 * lasts, and the blob goes once it has lasted its lifetime. The shriek blob
 * is {@code shriek every=40 reach=16 lasts=600}.
 * decision shriek-is-a-sculk-call-that-draws-every-mob-it-reaches
 *
 * @param every the ticks one pulse takes to travel out to the reach, evaluated each tick
 * @param reach the ring's radius at the end of a pulse, in blocks, evaluated each tick
 * @param lasts the ticks the blob calls for before it goes, evaluated each tick
 */
public record ShriekStep(Expr every, Expr reach, Expr lasts) implements Step {

    private static final String NAME = "shriek";
    private static final String FIELD_EVERY = "every";
    private static final String FIELD_REACH = "reach";
    private static final String FIELD_LASTS = "lasts";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<ShriekStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_EVERY).forGetter(ShriekStep::every),
            Expr.CODEC.fieldOf(FIELD_REACH).forGetter(ShriekStep::reach),
            Expr.CODEC.fieldOf(FIELD_LASTS).forGetter(ShriekStep::lasts)
    ).apply(inst, ShriekStep::new));

    /**
     * The registered type.
     */
    public static final StepType<ShriekStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<ShriekStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        int period = Math.max(1, every.evaluateInt(context));
        double radius = reach.evaluate(context);
        int phase = context.stepTicks() % period;
        context.hostAs(ShriekHost.class).callRing(ringRadius(phase, period, radius),
                ringRadius(phase + 1, period, radius));
        return context.stepTicks() + 1 >= lasts.evaluateInt(context);
    }

    /**
     * How far out a pulse's ring stands a number of ticks into its period.
     *
     * @param phase  the ticks since the pulse left the blob
     * @param period the ticks a pulse takes to reach the reach
     * @param reach  the ring's radius at the end of the pulse
     * @return the ring's radius, zero as the pulse leaves the blob
     */
    public static double ringRadius(int phase, int period, double reach) {
        return reach * phase / period;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(every, reach, lasts);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.SHRIEK);
    }
}
