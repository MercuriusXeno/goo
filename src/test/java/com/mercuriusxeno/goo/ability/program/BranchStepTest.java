package com.mercuriusxeno.goo.ability.program;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A branch step runs its then list when its condition reads nonzero and
 * its otherwise list when it reads zero (decision
 * aeon-mob-ritual-drops-spawn-egg).
 */
class BranchStepTest {

    private static BranchStep freezeOrRelease(double condition) {
        return new BranchStep(Expr.literal(condition),
                List.of(new SetAiStep(false)), List.of(new SetAiStep(true)));
    }

    private static StepHost entityHost() {
        StepHost host = mock(StepHost.class);
        when(host.kind()).thenReturn(HostKind.ENTITY);
        return host;
    }

    @Test
    void nonzeroConditionRunsOnlyTheThenList() {
        StepHost host = entityHost();

        ProgramBehavior.forHost(List.of(freezeOrRelease(1)), HostKind.ENTITY).tick(host);

        verify(host).setTargetAi(false);
        verify(host, never()).setTargetAi(true);
    }

    @Test
    void zeroConditionRunsOnlyTheOtherwiseList() {
        StepHost host = entityHost();

        ProgramBehavior.forHost(List.of(freezeOrRelease(0)), HostKind.ENTITY).tick(host);

        verify(host).setTargetAi(true);
        verify(host, never()).setTargetAi(false);
    }
}
