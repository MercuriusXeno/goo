package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityDefinition.BehaviorEntry;
import com.mercuriusxeno.goo.ability.ChainBehavior;
import com.mercuriusxeno.goo.block.ability.ChainMarkerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import java.util.List;

/**
 * The step runtime as a {@link ChainBehavior}: runs a step list against a
 * {@link StepHost}, one step at a time, starting the next step the same
 * tick the prior finishes. The marker's fuse runs before the program body,
 * so {@link #onFuseExpired} is the body's first tick. Only the cursor and
 * two tick counters are state, and they are what persists.
 *
 * <p>{@link #tick(StepHost)} is the whole runtime and takes any host, so a
 * test drives it against a mock with no level behind it (decision
 * host-agnostic-runtime).
 */
public final class ProgramBehavior implements ChainBehavior {

    /**
     * The behavior type name registered in {@code BehaviorType}.
     */
    public static final String TYPE_NAME = "program";

    private static final String TAG_STEP = "ProgramStep";
    private static final String TAG_STEP_TICKS = "ProgramStepTicks";
    private static final String TAG_PROGRAM_TICKS = "ProgramTicks";

    private final List<Step> steps;
    private int stepIndex;
    private int stepTicks;
    private int programTicks;

    /**
     * Creates the runtime over a step list.
     *
     * @param steps the program body in order
     */
    public ProgramBehavior(List<Step> steps) {
        this.steps = List.copyOf(steps);
    }

    /**
     * Factory for {@code BehaviorType} registration: the program body is
     * the entry's {@code steps} list.
     *
     * @param entry the behavior entry carrying the steps
     * @param def   the parent ability definition
     * @return the runtime over the entry's steps
     */
    public static ChainBehavior fromEntry(BehaviorEntry entry, AbilityDefinition def) {
        return new ProgramBehavior(entry.steps());
    }

    /**
     * Runs one tick: the current step ticks, and each step that finishes
     * hands the same tick to the next until a step stays running or the
     * body ends.
     *
     * @param host the host seam for this tick
     */
    public void tick(StepHost host) {
        while (isActive()) {
            Step current = steps.get(stepIndex);
            boolean finished = current.tick(new StepContext(host, stepTicks, programTicks));
            if (!finished) {
                stepTicks++;
                break;
            }
            stepIndex++;
            stepTicks = 0;
        }
        programTicks++;
    }

    /**
     * Returns the index of the step now running, or the step count once
     * the body has ended.
     *
     * @return the cursor
     */
    public int stepIndex() {
        return stepIndex;
    }

    @Override
    public void onFuseExpired(ServerLevel level, BlockPos pos, ChainMarkerBlockEntity be) {
        tick(new MarkerHost(level, pos, be));
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, ChainMarkerBlockEntity be) {
        tick(new MarkerHost(level, pos, be));
    }

    @Override
    public boolean isActive() {
        return stepIndex < steps.size();
    }

    @Override
    public void saveAdditional(ValueOutput output) {
        output.putInt(TAG_STEP, stepIndex);
        output.putInt(TAG_STEP_TICKS, stepTicks);
        output.putInt(TAG_PROGRAM_TICKS, programTicks);
    }

    @Override
    public void loadAdditional(ValueInput input) {
        stepIndex = input.getIntOr(TAG_STEP, 0);
        stepTicks = input.getIntOr(TAG_STEP_TICKS, 0);
        programTicks = input.getIntOr(TAG_PROGRAM_TICKS, 0);
    }
}
