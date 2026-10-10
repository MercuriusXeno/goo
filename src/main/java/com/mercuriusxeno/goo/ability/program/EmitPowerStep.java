package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Thumper's body step: the host block gives full redstone power for one
 * tick, then none, once every period, for the duration; then the step ends
 * with its power off, and the lingering block goes with it:
 * {@code emit_power period=40 duration=400}.
 * thumper-blob-pulses-periodically-then-fades
 *
 * @param period   the ticks from one pulse to the next, evaluated when the step runs
 * @param duration the ticks the step pulses for before it ends, evaluated when the step runs
 */
public record EmitPowerStep(Expr period, Expr duration) implements Step {

    private static final String NAME = "emit_power";
    private static final String FIELD_PERIOD = "period";
    private static final String FIELD_DURATION = "duration";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<EmitPowerStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_PERIOD).forGetter(EmitPowerStep::period),
            Expr.CODEC.fieldOf(FIELD_DURATION).forGetter(EmitPowerStep::duration)
    ).apply(inst, EmitPowerStep::new));

    /**
     * The registered type.
     */
    public static final StepType<EmitPowerStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<EmitPowerStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        PowerEmitHost host = context.hostAs(PowerEmitHost.class);
        boolean done = context.stepTicks() >= duration.evaluateInt(context);
        host.emitPower(!done && pulsesOn(context.stepTicks(), period.evaluateInt(context)));
        return done;
    }

    /**
     * Whether the block gives power on a tick of the step: on its first tick,
     * then once each period after.
     *
     * @param stepTicks the ticks the step has already run, zero on its first
     * @param period    the ticks from one pulse to the next; below 1 counts as 1
     * @return true on a pulsing tick
     */
    static boolean pulsesOn(int stepTicks, int period) {
        return stepTicks >= 0 && stepTicks % Math.max(1, period) == 0;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(period, duration);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TICKING, HostCapability.EMIT_POWER);
    }
}
