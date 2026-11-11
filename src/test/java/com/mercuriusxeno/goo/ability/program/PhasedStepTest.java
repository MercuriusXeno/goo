package com.mercuriusxeno.goo.ability.program;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A phased step enters a phase on its first tick, runs its tick list once a
 * tick for its duration, leaves on its last tick, and enters and leaves a
 * phase lasting no ticks in one tick (decision nether-stays-hand-written);
 * its phase cursor reads progress as ticks over duration.
 */
class PhasedStepTest {

    private static final int FIRST_PHASE_TICKS = 2;

    private final List<String> ran = new ArrayList<>();
    private int tick;

    /** A step that logs its label and the program tick it ran on, then finishes. */
    private Step logging(String label) {
        Step step = mock(Step.class);
        when(step.tick(any())).thenAnswer(call -> ran.add(label + "@" + tick));
        return step;
    }

    @Test
    void aPhaseEntersOnItsFirstTickTicksEachTickAndLeavesOnItsLast() {
        PhasedStep phased = new PhasedStep(Expr.literal(0), List.of(
                new StepPhase("timed", Expr.literal(FIRST_PHASE_TICKS),
                        List.of(logging("enter timed")), List.of(logging("tick timed")), List.of(logging("leave timed"))),
                new StepPhase("instant", Expr.literal(0),
                        List.of(logging("enter instant")), List.of(), List.of(logging("leave instant")))));
        MarkerHost host = mock(MarkerHost.class);
        when(host.phased()).thenReturn(new PhasedState());
        ProgramBehavior program = new ProgramBehavior(List.of(phased));

        while (program.isActive()) {
            tick++;
            program.tick(host);
        }

        assertEquals(List.of("enter timed@1", "tick timed@1", "tick timed@2", "leave timed@2",
                "enter instant@3", "leave instant@3"), ran);
    }

    @Test
    void progressIsTheTicksRunOverThePhasesDuration() {
        PhasedState state = new PhasedState();
        state.enter("timed", 3);
        state.countTick();

        assertEquals(1f / 3, state.progress());
    }
}
