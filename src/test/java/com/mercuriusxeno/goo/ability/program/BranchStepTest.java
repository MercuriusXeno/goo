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
 * aeon-mob-ritual-drops-spawn-egg). The lists hold explode steps of two
 * powers, a world call the host mock records; an effect step's change to
 * the entity is proven by the mob gametests (decision step-tick-holds-effect).
 */
class BranchStepTest {

    private static final float THEN_POWER = 1;
    private static final float OTHERWISE_POWER = 2;

    private static BranchStep blastByCondition(double condition) {
        return new BranchStep(Expr.literal(condition),
                List.of(new ExplodeStep(Expr.literal(THEN_POWER), ExplosionMode.NONE)),
                List.of(new ExplodeStep(Expr.literal(OTHERWISE_POWER), ExplosionMode.NONE)));
    }

    private static StepHost entityHost() {
        StepHost host = mock(StepHost.class);
        when(host.kind()).thenReturn(HostKind.ENTITY);
        return host;
    }

    @Test
    void nonzeroConditionRunsOnlyTheThenList() {
        StepHost host = entityHost();

        ProgramBehavior.forHost(List.of(blastByCondition(1)), HostKind.ENTITY).tick(host);

        verify(host).explode(THEN_POWER, ExplosionMode.NONE);
        verify(host, never()).explode(OTHERWISE_POWER, ExplosionMode.NONE);
    }

    @Test
    void zeroConditionRunsOnlyTheOtherwiseList() {
        StepHost host = entityHost();

        ProgramBehavior.forHost(List.of(blastByCondition(0)), HostKind.ENTITY).tick(host);

        verify(host).explode(OTHERWISE_POWER, ExplosionMode.NONE);
        verify(host, never()).explode(THEN_POWER, ExplosionMode.NONE);
    }
}
