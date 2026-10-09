package com.mercuriusxeno.goo.ability.program;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A drips step runs its children only once its count has gathered, and
 * starts the count over when it does.
 */
class DripsStepTest {

    private static final int COUNT = 6;

    /** A child step counting the ticks it ran. */
    private static final class Counting implements Step {
        private int runs;

        @Override
        public StepType<? extends Step> type() {
            return LeafSteps.WAIT.type();
        }

        @Override
        public boolean tick(StepContext context) {
            runs++;
            return true;
        }

        @Override
        public Stream<Expr> expressions() {
            return Stream.empty();
        }

        @Override
        public Set<HostCapability> requires() {
            return Set.of();
        }
    }

    @Test
    void dripsShortOfTheCountRunNothing() {
        DripHost host = mock(DripHost.class);
        when(host.countDrip()).thenReturn(COUNT - 1);
        Counting child = new Counting();
        new DripsStep(COUNT, List.of(child)).tick(new StepContext(host, 0, 0));
        assertEquals(0, child.runs);
        verify(host, never()).resetDrips();
    }

    @Test
    void theCountGatheredRunsTheChildrenAndStartsOver() {
        DripHost host = mock(DripHost.class);
        when(host.countDrip()).thenReturn(COUNT);
        Counting child = new Counting();
        new DripsStep(COUNT, List.of(child)).tick(new StepContext(host, 0, 0));
        assertEquals(1, child.runs);
        verify(host).resetDrips();
    }
}
