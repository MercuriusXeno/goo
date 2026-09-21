package com.mercuriusxeno.goo.ability.program;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.OptionalDouble;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A program loads for a host kind before its first tick: a damage-step
 * program on the struck entity host hurts the target, and a step or a
 * variable the host cannot serve refuses at load naming both.
 */
class ProgramHostLoadTest {

    private static final String ENTITY_LABEL = "struck entity";
    private static final String MARKER_LABEL = "marker block";

    private static Expr expr(String source) {
        return Expr.parse(source).getOrThrow();
    }

    private static StepHost entityHost(double health) {
        StepHost host = mock(StepHost.class);
        when(host.kind()).thenReturn(HostKind.ENTITY);
        when(host.read("health")).thenReturn(OptionalDouble.of(health));
        return host;
    }

    @Test
    void damageProgramOnEntityHostHurtsTheTargetWithTheConfiguredAmount() {
        StepHost host = entityHost(20);
        ProgramBehavior program = ProgramBehavior.forHost(
                List.of(new DamageStep(Expr.literal(8), DamageKind.MAGIC)), HostKind.ENTITY);

        program.tick(host);

        verify(host).damageTarget(8f, DamageKind.MAGIC);
        assertFalse(program.isActive());
    }

    @Test
    void damageAmountMayReadTheTargetsHealth() {
        StepHost host = entityHost(20);
        ProgramBehavior program = ProgramBehavior.forHost(
                List.of(new DamageStep(expr("health / 2"), DamageKind.FREEZE)), HostKind.ENTITY);

        program.tick(host);

        verify(host).damageTarget(10f, DamageKind.FREEZE);
    }

    @Test
    void markerOnlyStepOnEntityHostRefusesAtLoadNamingStepAndHost() {
        List<Step> steps = List.of(
                new AwaitEntityStep(SelectionShape.SPHERE, Expr.literal(3), List.of(EntityFilter.LIVING)),
                new DamageStep(Expr.literal(8), DamageKind.MAGIC));

        ProgramLoadException refusal = assertThrows(ProgramLoadException.class,
                () -> ProgramBehavior.forHost(steps, HostKind.ENTITY));

        assertTrue(refusal.getMessage().contains("await_entity"), refusal.getMessage());
        assertTrue(refusal.getMessage().contains(ENTITY_LABEL), refusal.getMessage());
    }

    @Test
    void waitingStepOnEntityHostRefusesSinceNothingTicksAnEntity() {
        ProgramLoadException refusal = assertThrows(ProgramLoadException.class,
                () -> ProgramBehavior.forHost(List.of(new WaitStep(Expr.literal(2))), HostKind.ENTITY));

        assertTrue(refusal.getMessage().contains("wait"), refusal.getMessage());
        assertTrue(refusal.getMessage().contains("ticking"), refusal.getMessage());
    }

    @Test
    void markerVariableOnEntityHostRefusesAtLoadNamingVariableAndHost() {
        ProgramLoadException refusal = assertThrows(ProgramLoadException.class,
                () -> ProgramBehavior.forHost(
                        List.of(new DamageStep(expr("4 + stacks"), DamageKind.MAGIC)), HostKind.ENTITY));

        assertTrue(refusal.getMessage().contains("stacks"), refusal.getMessage());
        assertTrue(refusal.getMessage().contains(ENTITY_LABEL), refusal.getMessage());
    }

    @Test
    void damageStepOnMarkerHostRefusesAtLoad() {
        ProgramLoadException refusal = assertThrows(ProgramLoadException.class,
                () -> ProgramBehavior.forHost(
                        List.of(new DamageStep(Expr.literal(8), DamageKind.MAGIC)), HostKind.MARKER));

        assertTrue(refusal.getMessage().contains("damage"), refusal.getMessage());
        assertTrue(refusal.getMessage().contains(MARKER_LABEL), refusal.getMessage());
    }

    @Test
    void unstableProgramsLoadForTheMarkerHost() {
        List<Step> mine = List.of(
                new AwaitEntityStep(SelectionShape.SPHERE, Expr.literal(3), List.of(EntityFilter.LIVING)),
                new ExplodeStep(expr("2.5 + 1.0 * (stacks - 1)"), ExplosionMode.TNT));

        assertDoesNotThrow(() -> ProgramBehavior.forHost(mine, HostKind.MARKER));
    }

    @Test
    void tickVariableIsBoundForEveryHost() {
        assertDoesNotThrow(() -> ProgramBehavior.forHost(
                List.of(new DamageStep(expr("tick + 1"), DamageKind.MAGIC)), HostKind.ENTITY));
    }
}
