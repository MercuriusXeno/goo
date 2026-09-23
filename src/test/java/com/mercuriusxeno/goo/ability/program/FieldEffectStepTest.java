package com.mercuriusxeno.goo.ability.program;

import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mojang.serialization.JsonOps;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The metal_spikes program, decoded from its JSON, composes the
 * field-effect-with-controller sub-chain: on a Mockito marker host whose
 * scan hands over the entities in radius that the filters keep, a target
 * costs one stack and is impaled on the strike tick, a sneaking player
 * costs none, and a spent budget tears down once and ends the program.
 */
class FieldEffectStepTest {

    private static final String METAL_SPIKES = "/data/goo/goo_abilities/metal_spikes.json";
    private static final int WALKER_ID = 7;
    private static final int SNEAKER_ID = 8;
    private static final int OTHER_WALKER_ID = 9;
    private static final int STRIKE_TICK = 6;
    private static final int STRIKE_TICKS = 13;
    private static final int COOLDOWN = 10;
    private static final float IMPALE_DAMAGE = 6f;
    private static final int IDLE_TICKS = 40;

    private static List<Step> metalProgram() throws IOException {
        try (InputStream in = FieldEffectStepTest.class.getResourceAsStream(METAL_SPIKES)) {
            assertNotNull(in, "Classpath holds no " + METAL_SPIKES);
            Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
            AbilityDefinition def = AbilityDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader))
                    .getOrThrow(IllegalStateException::new);
            return def.behaviors().get(0).steps();
        }
    }

    /**
     * A living entity in the trap's radius, bound as a target host.
     *
     * @param id       its entity id
     * @param sneaking whether it is a sneaking player, which not_sneaking rejects
     * @return the target host
     */
    private static StepHost entityInRadius(int id, boolean sneaking) {
        StepHost target = mock(StepHost.class);
        when(target.kind()).thenReturn(HostKind.ENTITY);
        when(target.targetId()).thenReturn(id);
        when(target.targetCenter()).thenReturn(new Vec3(1.5, 1.0, 0.5));
        when(target.read(anyString())).thenReturn(OptionalDouble.empty());
        when(target.targetPasses(anySet())).thenAnswer(inv -> {
            Set<EntityFilter> filters = inv.getArgument(0);
            return !(sneaking && filters.contains(EntityFilter.NOT_SNEAKING));
        });
        return target;
    }

    /**
     * A marker host holding stacks, keeping a real field-effect state, and
     * standing in a world whose scan hands the body each entity in radius
     * that every filter keeps.
     *
     * @param stacks   the live stack count, spent by decrementStack
     * @param inRadius the entities within the trap's radius
     * @return the marker host
     */
    private static StepHost marker(AtomicInteger stacks, List<StepHost> inRadius) {
        StepHost host = mock(StepHost.class);
        FieldEffectState state = new FieldEffectState();
        when(host.kind()).thenReturn(HostKind.MARKER);
        when(host.fieldEffect()).thenReturn(state);
        when(host.read(anyString())).thenReturn(OptionalDouble.empty());
        when(host.stackCount()).thenAnswer(inv -> stacks.get());
        doAnswer(inv -> stacks.decrementAndGet()).when(host).decrementStack();
        doAnswer(inv -> {
            Set<EntityFilter> filters = inv.getArgument(2);
            Consumer<StepHost> body = inv.getArgument(3);
            inRadius.stream().filter(entity -> entity.targetPasses(filters)).forEach(body);
            return null;
        }).when(host).forEachEntityWithin(any(), anyDouble(), anySet(), any());
        doAnswer(inv -> {
            int id = inv.getArgument(0);
            Consumer<StepHost> body = inv.getArgument(1);
            inRadius.stream().filter(entity -> entity.targetId() == id).forEach(body);
            return null;
        }).when(host).forEntity(anyInt(), any());
        return host;
    }

    private static void tick(ProgramBehavior program, StepHost host, int ticks) {
        for (int i = 0; i < ticks; i++) {
            program.tick(host);
        }
    }

    @Test
    void aTargetInRadiusCostsOneStackAndIsImpaledOnTheStrikeTick() throws IOException {
        AtomicInteger stacks = new AtomicInteger(2);
        StepHost walker = entityInRadius(WALKER_ID, false);
        StepHost host = marker(stacks, List.of(walker));
        ProgramBehavior program = ProgramBehavior.forHost(metalProgram(), HostKind.MARKER);

        program.tick(host);

        assertEquals(1, stacks.get());
        verify(walker, never()).damageTarget(IMPALE_DAMAGE, DamageKind.STALAGMITE, false);
        tick(program, host, STRIKE_TICK);
        verify(walker).damageTarget(IMPALE_DAMAGE, DamageKind.STALAGMITE, false);
        verify(walker).spawnParticles(eq(FxAnchor.TARGET), argThat(burst -> "crit".equals(burst.particle().getPath())));
        assertEquals(1, stacks.get());
        assertTrue(program.isActive());
    }

    @Test
    void aSneakingPlayerCostsNoStackAndTakesNoHit() throws IOException {
        AtomicInteger stacks = new AtomicInteger(2);
        StepHost sneaker = entityInRadius(SNEAKER_ID, true);
        StepHost host = marker(stacks, List.of(sneaker));
        ProgramBehavior program = ProgramBehavior.forHost(metalProgram(), HostKind.MARKER);

        tick(program, host, IDLE_TICKS);

        assertEquals(2, stacks.get());
        verify(sneaker, never()).damageTarget(anyFloat(), any(), anyBoolean());
        verify(host, never()).decrementStack();
        assertTrue(program.isActive());
    }

    @Test
    void theCooldownHoldsTheNextStrikeTenTicks() throws IOException {
        AtomicInteger stacks = new AtomicInteger(3);
        StepHost first = entityInRadius(WALKER_ID, false);
        StepHost second = entityInRadius(OTHER_WALKER_ID, false);
        StepHost host = marker(stacks, List.of(first, second));
        ProgramBehavior program = ProgramBehavior.forHost(metalProgram(), HostKind.MARKER);

        tick(program, host, COOLDOWN);
        assertEquals(2, stacks.get());

        program.tick(host);
        assertEquals(1, stacks.get());
    }

    @Test
    void aSpentBudgetTearsDownOnceWhenTheLastStrikeRetractsAndEndsTheProgram() throws IOException {
        AtomicInteger stacks = new AtomicInteger(1);
        StepHost walker = entityInRadius(WALKER_ID, false);
        StepHost host = marker(stacks, List.of(walker));
        ProgramBehavior program = ProgramBehavior.forHost(metalProgram(), HostKind.MARKER);

        tick(program, host, STRIKE_TICKS - 1);
        assertEquals(0, stacks.get());
        assertTrue(program.isActive());
        verify(host, never()).playSound(any(), any());

        tick(program, host, IDLE_TICKS);

        assertFalse(program.isActive());
        verify(host, times(1)).spawnParticles(eq(FxAnchor.HOST),
                argThat(burst -> "poof".equals(burst.particle().getPath())));
        verify(host, times(1)).playSound(eq(FxAnchor.HOST),
                argThat(cue -> "block.fire.extinguish".equals(cue.sound().getPath())));
    }

    @Test
    void theRunningFieldEffectAllowsTopOff() throws IOException {
        AtomicInteger stacks = new AtomicInteger(1);
        StepHost host = marker(stacks, List.of());
        ProgramBehavior program = ProgramBehavior.forHost(metalProgram(), HostKind.MARKER);

        program.tick(host);

        assertTrue(program.allowsTopOff());
    }

    @Test
    void theStrikeBodyLoadsAgainstTheEntityHostAndTheFieldEffectRefusesTheEntityHost() throws IOException {
        List<Step> metal = metalProgram();

        assertDoesNotThrow(() -> ProgramBehavior.forHost(metal, HostKind.MARKER));
        ProgramLoadException refusal = assertThrows(ProgramLoadException.class,
                () -> ProgramBehavior.forHost(metal, HostKind.ENTITY));
        assertTrue(refusal.getMessage().contains("field_effect"), refusal.getMessage());
    }

    @Test
    void aMarkerOnlyStepInTheStrikeBodyRefusesAtLoadSinceItRunsOnTheStruckEntity() {
        List<Step> steps = List.of(new FieldEffectStep(Expr.literal(3), List.of(), Expr.literal(0),
                Expr.literal(1), Expr.literal(0), Expr.literal(1),
                List.of(new ExplodeStep(Expr.literal(2), ExplosionMode.NONE),
                        new WaitStep(Expr.literal(2))),
                List.of()));

        ProgramLoadException refusal = assertThrows(ProgramLoadException.class,
                () -> ProgramBehavior.forHost(steps, HostKind.MARKER));

        assertTrue(refusal.getMessage().contains("wait"), refusal.getMessage());
        assertTrue(refusal.getMessage().contains("struck entity"), refusal.getMessage());
    }
}
