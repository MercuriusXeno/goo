package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A program loads for the tap landing host: a particles burst runs once at
 * the host anchor, and a step needing a target or a later tick refuses at
 * load naming the step and the tap host. The host anchors at the struck
 * face's center.
 */
class TapHostTest {

    private static final String TAP_LABEL = "tap landing";
    private static final String TARGET_NEED = "needs " + HostCapability.TARGET.key();
    private static final Identifier SPLASH = Identifier.withDefaultNamespace("splash");
    private static final int BURST = 6;

    private static ParticlesStep splash(FxAnchor at) {
        return new ParticlesStep(SPLASH, at, Expr.literal(BURST), Expr.literal(0.2),
                Optional.empty(), Optional.empty(), Expr.literal(0), Expr.literal(0));
    }

    private static void assertRefusal(ProgramLoadException refusal, String step) {
        assertTrue(refusal.getMessage().contains(step), refusal.getMessage());
        assertTrue(refusal.getMessage().contains(TAP_LABEL), refusal.getMessage());
    }

    @Test
    void particlesProgramRunsOnceAtTheHostAnchor() {
        TapHost host = mock(TapHost.class);
        when(host.kind()).thenReturn(HostKind.TAP);
        ProgramBehavior program = ProgramBehavior.forHost(List.of(splash(FxAnchor.HOST)), HostKind.TAP);

        program.tick(host);

        ArgumentCaptor<ParticleBurst> burst = ArgumentCaptor.forClass(ParticleBurst.class);
        verify(host, times(1)).spawnParticles(burst.capture());
        assertEquals(SPLASH, burst.getValue().particle());
        assertEquals(BURST, burst.getValue().count());
        assertFalse(program.isActive());
    }

    @Test
    void damageProgramRefusesAtLoadNamingStepAndHost() {
        List<Step> steps = List.of(new DamageStep(Expr.literal(8), DamageKind.MAGIC));

        assertRefusal(assertThrows(ProgramLoadException.class, () -> ProgramBehavior.forHost(steps, HostKind.TAP)),
                "damage");
    }

    @Test
    void setBabyProgramRefusesAtLoadNamingTheTargetCapability() {
        List<Step> steps = List.of(new SetBabyStep(true));

        ProgramLoadException refusal = assertThrows(ProgramLoadException.class,
                () -> ProgramBehavior.forHost(steps, HostKind.TAP));

        assertRefusal(refusal, "set_baby");
        assertTrue(refusal.getMessage().contains(TARGET_NEED), refusal.getMessage());
    }

    @Test
    void particlesAtTheTargetRefuseAtLoad() {
        List<Step> steps = List.of(splash(FxAnchor.TARGET));

        assertRefusal(assertThrows(ProgramLoadException.class, () -> ProgramBehavior.forHost(steps, HostKind.TAP)),
                "particles");
    }

    @Test
    void waitingStepRefusesAtLoad() {
        List<Step> steps = List.of(new WaitStep(Expr.literal(2)));

        assertRefusal(assertThrows(ProgramLoadException.class, () -> ProgramBehavior.forHost(steps, HostKind.TAP)),
                "wait");
    }

    @Test
    void hostProvidesNoTargetStacksOrTicks() {
        assertFalse(HostKind.TAP.capabilities().contains(HostCapability.TARGET));
        assertFalse(HostKind.TAP.capabilities().contains(HostCapability.STACKS));
        assertFalse(HostKind.TAP.capabilities().contains(HostCapability.TICKING));
        assertTrue(HostKind.TAP.capabilities().contains(HostCapability.PLACE_BLOCK));
    }

    @Test
    void anchorIsTheStruckFaceCenter() {
        BlockPos landing = new BlockPos(4, 60, -9);

        assertEquals(new Vec3(4.5, 61.0, -8.5), TapHost.faceCenter(landing, Direction.UP));
        assertEquals(new Vec3(5.0, 60.5, -8.5), TapHost.faceCenter(landing, Direction.EAST));
    }
}
