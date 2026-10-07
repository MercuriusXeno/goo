package com.mercuriusxeno.goo.ability.program;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ProgramBehavior drives a step list against a MarkerHost with no level
 * behind it: instant steps chain within a tick, waiting steps hold the
 * cursor, and the behavior reads inactive once the body ends.
 */
class ProgramBehaviorTest {

    private static final String STACKS = "stacks";
    private static final Set<EntityFilter> LIVING = Set.of(EntityFilter.LIVING);

    private static Expr expr(String source) {
        return Expr.parse(source).getOrThrow();
    }

    private static MarkerHost hostWithStacks(int stacks) {
        MarkerHost host = mock(MarkerHost.class);
        when(host.read(STACKS)).thenReturn(OptionalDouble.of(stacks));
        return host;
    }

    private static ExplodeStep explode(String power) {
        return new ExplodeStep(expr(power), ExplosionMode.TNT);
    }

    @Test
    void explodePowerIsAnExpressionOverTheHostStackCount() {
        MarkerHost host = hostWithStacks(3);
        ProgramBehavior program = new ProgramBehavior(List.of(explode("2 + 1 * (stacks - 1)")));

        program.tick(host);

        verify(host).explode(4f, ExplosionMode.TNT);
        assertFalse(program.isActive());
    }

    @Test
    void waitHoldsTheCursorForItsTicks() {
        MarkerHost host = hostWithStacks(1);
        ProgramBehavior program = new ProgramBehavior(List.of(LeafSteps.WAIT.step(Expr.literal(2)), explode("1")));

        program.tick(host);
        program.tick(host);
        verify(host, never()).explode(anyFloat(), any());
        assertTrue(program.isActive());

        program.tick(host);
        verify(host).explode(1f, ExplosionMode.TNT);
        assertFalse(program.isActive());
    }

    @Test
    void awaitEntityHoldsUntilTheHostSeesOne() {
        MarkerHost host = hostWithStacks(2);
        when(host.anyEntityWithin(SelectionShape.SPHERE, 3.0, LIVING)).thenReturn(false, false, true);
        ProgramBehavior program = new ProgramBehavior(List.of(
                new AwaitEntityStep(SelectionShape.SPHERE, Expr.literal(3), List.of(EntityFilter.LIVING)),
                explode("2.5 + 1.0 * (stacks - 1)")));

        program.tick(host);
        program.tick(host);
        assertTrue(program.isActive());
        verify(host, never()).explode(anyFloat(), any());

        program.tick(host);
        verify(host).explode(3.5f, ExplosionMode.TNT);
        assertFalse(program.isActive());
    }

    @Test
    void instantStepsChainWithinOneTick() {
        MarkerHost host = hostWithStacks(1);
        ProgramBehavior program = new ProgramBehavior(List.of(LeafSteps.WAIT.step(Expr.literal(0)), explode("1"), explode("2")));

        program.tick(host);

        verify(host).explode(1f, ExplosionMode.TNT);
        verify(host).explode(2f, ExplosionMode.TNT);
        assertEquals(3, program.stepIndex());
    }

    @Test
    void finishedProgramTicksNothing() {
        MarkerHost host = hostWithStacks(1);
        ProgramBehavior program = new ProgramBehavior(List.of(explode("1")));

        program.tick(host);
        program.tick(host);

        verify(host, times(1)).explode(anyFloat(), any());
        verify(host, never()).anyEntityWithin(any(), anyDouble(), any());
    }

    @Test
    void emptyProgramIsInactiveAtOnce() {
        assertFalse(new ProgramBehavior(List.of()).isActive());
    }

    /** A framed sound plays on its frame while the program runs on (decision ability-json-names-its-choreography). */
    @Nested
    class SoundFrame {
        private static final Identifier SHINK = Identifier.parse("minecraft:item.trident.return");
        private static final SoundCue CUE = new SoundCue(SHINK, SoundKind.BLOCKS, 1f, 1f);
        private static final int FRAME = 3;

        private SoundStep shink(Expr frame) {
            return new SoundStep(SHINK, FxAnchor.HOST, SoundKind.BLOCKS, Expr.literal(1), Expr.literal(1), frame);
        }

        @Test
        void framedSoundPlaysOnItsFrameWhileTheNextStepRunsFromTheFirstTick() {
            MarkerHost host = hostWithStacks(1);
            ProgramBehavior program = new ProgramBehavior(
                    List.of(shink(Expr.literal(FRAME)), LeafSteps.WAIT.step(Expr.literal(10))));

            program.tick(host);
            assertEquals(1, program.stepIndex(), "the step after the sound waited on it");
            for (int tick = 1; tick < FRAME; tick++) {
                program.tick(host);
            }
            verify(host, never()).playSound(any());

            program.tick(host);
            verify(host, times(1)).playSound(CUE);
            program.tick(host);
            verify(host, times(1)).playSound(CUE);
        }

        @Test
        void soundNamingNoFramePlaysTheTickItIsReached() {
            MarkerHost host = hostWithStacks(1);
            SoundStep unframed = (SoundStep) StepTypes.LIST_CODEC.parse(JsonOps.INSTANCE,
                    JsonParser.parseString(
                            "[{\"type\": \"sound\", \"id\": \"minecraft:item.trident.return\"}]"))
                    .getOrThrow().getFirst();
            ProgramBehavior program = new ProgramBehavior(List.of(unframed, LeafSteps.WAIT.step(Expr.literal(10))));

            program.tick(host);

            verify(host, times(1)).playSound(CUE);
            assertEquals(1, program.stepIndex());
        }
    }
}
