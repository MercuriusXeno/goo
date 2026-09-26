package com.mercuriusxeno.goo.ability.program;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
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

    private static EntityHost entityHost(double health) {
        EntityHost host = mock(EntityHost.class);
        when(host.kind()).thenReturn(HostKind.ENTITY);
        when(host.read("health")).thenReturn(OptionalDouble.of(health));
        return host;
    }

    @Test
    void explodeProgramOnEntityHostBlastsWithTheConfiguredPowerAndEnds() {
        EntityHost host = entityHost(20);
        ProgramBehavior program = ProgramBehavior.forHost(
                List.of(new ExplodeStep(Expr.literal(8), ExplosionMode.NONE)), HostKind.ENTITY);

        program.tick(host);

        verify(host).explode(8f, ExplosionMode.NONE);
        assertFalse(program.isActive());
    }

    @Test
    void anAmountMayReadTheTargetsHealth() {
        EntityHost host = entityHost(20);
        ProgramBehavior program = ProgramBehavior.forHost(
                List.of(new ExplodeStep(expr("health / 2"), ExplosionMode.NONE)), HostKind.ENTITY);

        program.tick(host);

        verify(host).explode(10f, ExplosionMode.NONE);
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
    void waitingStepInsideATargetSelectionRefusesNamingTheChild() {
        List<Step> steps = List.of(new TargetStep(List.of(), List.of(LeafSteps.WAIT.step(Expr.literal(2)))));

        ProgramLoadException refusal = assertThrows(ProgramLoadException.class,
                () -> ProgramBehavior.forHost(steps, HostKind.ENTITY));

        assertTrue(refusal.getMessage().contains("wait"), refusal.getMessage());
        assertTrue(refusal.getMessage().contains(ENTITY_LABEL), refusal.getMessage());
    }

    @Test
    void waitingStepOnEntityHostRefusesSinceNothingTicksAnEntity() {
        ProgramLoadException refusal = assertThrows(ProgramLoadException.class,
                () -> ProgramBehavior.forHost(List.of(LeafSteps.WAIT.step(Expr.literal(2))), HostKind.ENTITY));

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
    void entitiesSelectionOnMarkerHostHoldsItsChildrenToTheStruckEntityHost() {
        assertDoesNotThrow(() -> ProgramBehavior.forHost(List.of(new EntitiesStep(SelectionShape.SPHERE,
                Expr.literal(2.5), List.of(EntityFilter.LIVING),
                List.of(new DamageStep(expr("health * 0.5"), DamageKind.MAGIC)))), HostKind.MARKER));

        List<Step> markerChild = List.of(new EntitiesStep(SelectionShape.SPHERE, Expr.literal(2.5),
                List.of(EntityFilter.LIVING), List.of(LeafSteps.WAIT.step(Expr.literal(5)))));
        ProgramLoadException refusal = assertThrows(ProgramLoadException.class,
                () -> ProgramBehavior.forHost(markerChild, HostKind.MARKER));

        assertTrue(refusal.getMessage().contains("wait"), refusal.getMessage());
        assertTrue(refusal.getMessage().contains(ENTITY_LABEL), refusal.getMessage());
    }

    @Test
    void particlesAtTheTargetRefuseOnTheMarkerHostWhileParticlesAtTheHostLoad() {
        ProgramLoadException refusal = assertThrows(ProgramLoadException.class,
                () -> ProgramBehavior.forHost(List.of(particles(FxAnchor.TARGET)), HostKind.MARKER));

        assertTrue(refusal.getMessage().contains("particles"), refusal.getMessage());
        assertTrue(refusal.getMessage().contains(MARKER_LABEL), refusal.getMessage());
        assertDoesNotThrow(() -> ProgramBehavior.forHost(List.of(particles(FxAnchor.HOST)), HostKind.MARKER));
    }

    @Test
    void teleportOnMarkerHostRefusesSinceItMovesATarget() {
        ProgramLoadException refusal = assertThrows(ProgramLoadException.class,
                () -> ProgramBehavior.forHost(
                        List.of(new TeleportStep(TeleportMode.RANDOM_OFFSET, Expr.literal(32))), HostKind.MARKER));

        assertTrue(refusal.getMessage().contains("teleport"), refusal.getMessage());
        assertTrue(refusal.getMessage().contains(MARKER_LABEL), refusal.getMessage());
    }

    private static ParticlesStep particles(FxAnchor at) {
        return new ParticlesStep(Identifier.parse("minecraft:crit"), at, Expr.literal(1), Expr.literal(0),
                Optional.empty(), Optional.empty(), Expr.literal(0), Expr.literal(0));
    }

    @Test
    void unstableProgramsLoadForTheMarkerHost() {
        List<Step> mine = List.of(
                new AwaitEntityStep(SelectionShape.SPHERE, Expr.literal(3), List.of(EntityFilter.LIVING)),
                new ExplodeStep(expr("2.5 + 1.0 * (stacks - 1)"), ExplosionMode.TNT));

        assertDoesNotThrow(() -> ProgramBehavior.forHost(mine, HostKind.MARKER));
    }

    @Test
    void glowCrystalProgramLoadsForTheMarkerHostAndRefusesTheEntityHost() {
        List<Step> glow = List.of(new PlaceBlockStep(Identifier.parse("goo:glow_crystal"), Map.of(
                "facing", new StateValue.PlacedFace(),
                "size", new StateValue.Pick(expr("stacks - 1"), List.of("tiny", "large")))));

        assertDoesNotThrow(() -> ProgramBehavior.forHost(glow, HostKind.MARKER));
        ProgramLoadException refusal = assertThrows(ProgramLoadException.class,
                () -> ProgramBehavior.forHost(glow, HostKind.ENTITY));

        assertTrue(refusal.getMessage().contains("place_block"), refusal.getMessage());
        assertTrue(refusal.getMessage().contains(ENTITY_LABEL), refusal.getMessage());
    }

    @Test
    void progressiveAreaProgramLoadsForTheMarkerHostAndRefusesTheEntityHost() {
        List<Step> rock = List.of(new ProgressiveAreaStep(AreaShape.TUNNEL, "silk_break", "rock_dust",
                "stone_break", Expr.literal(8)));

        assertDoesNotThrow(() -> ProgramBehavior.forHost(rock, HostKind.MARKER));
        ProgramLoadException refusal = assertThrows(ProgramLoadException.class,
                () -> ProgramBehavior.forHost(rock, HostKind.ENTITY));

        assertTrue(refusal.getMessage().contains("progressive_area"), refusal.getMessage());
        assertTrue(refusal.getMessage().contains(ENTITY_LABEL), refusal.getMessage());
    }

    @Test
    void tickVariableIsBoundForEveryHost() {
        assertDoesNotThrow(() -> ProgramBehavior.forHost(
                List.of(new DamageStep(expr("tick + 1"), DamageKind.MAGIC)), HostKind.ENTITY));
    }

    private static final Map<HostKind, Class<? extends StepHost>> HOST_TYPES = Map.of(
            HostKind.MARKER, MarkerHost.class, HostKind.ENTITY, EntityHost.class, HostKind.TAP, TapHost.class);

    /**
     * A step needing exactly one capability, standing in for whichever
     * real step needs it.
     *
     * @param needed the capability
     * @return the step
     */
    private static Step needing(HostCapability needed) {
        Step step = mock(Step.class);
        when(step.requires()).thenReturn(Set.of(needed));
        doReturn(LeafSteps.WAIT.type()).when(step).type();
        return step;
    }

    @Test
    void eachKindProvidesTheCapabilityInterfacesItsHostImplements() {
        assertEquals(EnumSet.complementOf(EnumSet.of(HostCapability.TARGET)), HostKind.MARKER.capabilities());
        assertEquals(Set.of(HostCapability.TARGET, HostCapability.EXPLODE, HostCapability.ENTITY_SCAN),
                HostKind.ENTITY.capabilities());
        assertEquals(Set.of(HostCapability.EXPLODE, HostCapability.ENTITY_SCAN, HostCapability.PLACE_BLOCK),
                HostKind.TAP.capabilities());
    }

    @ParameterizedTest
    @EnumSource(HostCapability.class)
    void aStepLoadsExactlyWhereTheHostImplementsTheInterfaceItNeeds(HostCapability needed) {
        for (HostKind kind : HostKind.values()) {
            List<Step> steps = List.of(needing(needed));
            if (needed.hostType().isAssignableFrom(HOST_TYPES.get(kind))) {
                assertDoesNotThrow(() -> ProgramBehavior.forHost(steps, kind), kind + " " + needed);
            } else {
                ProgramLoadException refusal = assertThrows(ProgramLoadException.class,
                        () -> ProgramBehavior.forHost(steps, kind), kind + " " + needed);
                assertTrue(refusal.getMessage().contains(needed.key()), refusal.getMessage());
                assertTrue(refusal.getMessage().contains(kind.label()), refusal.getMessage());
            }
        }
    }

    @Test
    void aHostTypeProvidesTheCapabilitiesOfTheInterfacesItExtends() {
        interface ScanAndTargetHost extends EntityScanHost, TargetHost {
        }

        assertEquals(Set.of(HostCapability.ENTITY_SCAN, HostCapability.TARGET),
                HostCapability.providedBy(ScanAndTargetHost.class));
        assertEquals(Set.of(), HostCapability.providedBy(StepHost.class));
    }
}