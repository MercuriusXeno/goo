package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The phased sub-chain as one step (decision nether-stays-hand-written):
 * ordered phases, each with a duration, steps run on entering it, on each
 * of its ticks and on leaving it. A phase is entered on its first tick,
 * runs its tick steps once per tick for its duration, then runs its leave
 * steps and hands the next tick to the next phase; a phase lasting zero
 * ticks enters and leaves in one tick. The step finishes when the last
 * phase leaves.
 *
 * <p>The cursor lives in the host's {@link PhasedState}, which the host's
 * renderer reads with the declared {@code radius}: the nether black hole
 * grows its sphere through {@code expand}, holds it, and shrinks it
 * through {@code contract}.
 *
 * @param radius the reach the host's renderer draws, evaluated each tick
 * @param phases the phases in order
 */
public record PhasedStep(Expr radius, List<StepPhase> phases) implements Step {

    private static final String NAME = "phased";
    private static final String FIELD_RADIUS = "radius";
    private static final String FIELD_PHASES = "phases";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<PhasedStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.optionalFieldOf(FIELD_RADIUS, Expr.literal(0)).forGetter(PhasedStep::radius),
            StepPhase.CODEC.listOf(1, Integer.MAX_VALUE).fieldOf(FIELD_PHASES).forGetter(PhasedStep::phases)
    ).apply(inst, PhasedStep::new));

    /**
     * The registered type.
     */
    public static final StepType<PhasedStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<PhasedStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        StepHost host = context.host();
        PhasedState state = host.phased();
        state.setRadius(radius.evaluateFloat(context));
        StepPhase phase = phases.get(state.index());
        if (state.ticks() == 0) {
            state.enter(phase.name(), phase.ticks().evaluateInt(context));
            run(phase.enter(), host);
        }
        if (state.ticks() < state.duration()) {
            run(phase.tick(), host);
            state.countTick();
        }
        if (state.ticks() < state.duration()) {
            return false;
        }
        run(phase.leave(), host);
        return advance(state);
    }

    /**
     * Moves the cursor past the phase that just left, clearing it after
     * the last phase.
     *
     * @param state the phased state
     * @return true when the last phase has left
     */
    private boolean advance(PhasedState state) {
        int next = state.index() + 1;
        if (next >= phases.size()) {
            state.finish();
            return true;
        }
        state.advance(phases.get(next).name());
        return false;
    }

    /**
     * Runs a phase's steps on the host within this tick.
     *
     * @param steps the steps to run
     * @param host  the host
     */
    private static void run(List<Step> steps, StepHost host) {
        if (!steps.isEmpty()) {
            new ProgramBehavior(steps).tick(host);
        }
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.concat(Stream.of(radius), phases.stream().map(StepPhase::ticks));
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TICKING, HostCapability.PHASED);
    }

    @Override
    public Stream<Step> children() {
        return phases.stream().flatMap(StepPhase::steps);
    }
}
