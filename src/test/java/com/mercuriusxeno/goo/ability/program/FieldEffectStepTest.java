package com.mercuriusxeno.goo.ability.program;

import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mojang.serialization.JsonOps;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
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
 * The metal_spikes and crystal_cloud programs, decoded from their JSON,
 * compose the field-effect-with-controller sub-chain. On a Mockito marker
 * host whose scan hands over the entities in radius that the filters keep:
 * a metal target costs one stack and is impaled on the strike tick, a
 * sneaking player costs none, and a spent budget tears down once and ends
 * the program; eight crystal shreds spend one stack, a sprinting player is
 * shredded twice as often, a standing entity not at all, and the cloud
 * expands, then contracts once its last blob is spent.
 */
class FieldEffectStepTest {

    private static final String METAL_SPIKES = "/data/goo/goo_abilities/metal_spikes.json";
    private static final String CRYSTAL_CLOUD = "/data/goo/goo_abilities/crystal_cloud.json";
    private static final int WALKER_ID = 7;
    private static final int SNEAKER_ID = 8;
    private static final int OTHER_WALKER_ID = 9;
    private static final int STRIKE_TICK = 6;
    private static final int STRIKE_TICKS = 13;
    private static final int COOLDOWN = 10;
    private static final float IMPALE_DAMAGE = 6f;
    private static final int IDLE_TICKS = 40;
    private static final float SHRED_DAMAGE = 1f;
    private static final int CHARGES_PER_BLOB = 8;
    /** A walker is shredded every second tick, so a blob's eight charges last sixteen ticks. */
    private static final int WALKING_TICKS_FOR_A_BLOB = 16;
    private static final int SPRINT_WINDOW = 6;
    private static final int CLOUD_ANIMATION_TICKS = 10;
    private static final float FRACTION_TOLERANCE = 1e-5f;

    private static List<Step> program(String resource) throws IOException {
        try (InputStream in = FieldEffectStepTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, "Classpath holds no " + resource);
            Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
            AbilityDefinition def = AbilityDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader))
                    .getOrThrow(IllegalStateException::new);
            return def.behaviors().get(0).steps();
        }
    }

    private static List<Step> metalProgram() throws IOException {
        return program(METAL_SPIKES);
    }

    private static List<Step> crystalProgram() throws IOException {
        return program(CRYSTAL_CLOUD);
    }

    /**
     * A living entity in the field's radius, bound as a target host, that
     * every filter keeps.
     *
     * @param id its entity id
     * @return the target host
     */
    private static StepHost walker(int id) {
        return entityInRadius(id, null, false);
    }

    /**
     * A living entity in the field's radius, bound as a target host.
     *
     * @param id         its entity id
     * @param rejectedBy the one filter that rejects it, or null when every filter keeps it
     * @param sprinting  whether it is a sprinting player
     * @return the target host
     */
    private static StepHost entityInRadius(int id, @Nullable EntityFilter rejectedBy, boolean sprinting) {
        StepHost target = mock(StepHost.class);
        when(target.kind()).thenReturn(HostKind.ENTITY);
        when(target.targetId()).thenReturn(id);
        when(target.targetCenter()).thenReturn(new Vec3(1.5, 1.0, 0.5));
        when(target.read(anyString())).thenReturn(OptionalDouble.empty());
        when(target.read(HostVariables.SPRINTING)).thenReturn(OptionalDouble.of(sprinting ? 1 : 0));
        when(target.targetPasses(anySet())).thenAnswer(inv -> {
            Set<EntityFilter> filters = inv.getArgument(0);
            return rejectedBy == null || !filters.contains(rejectedBy);
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
        StepHost walker = walker(WALKER_ID);
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
        StepHost sneaker = entityInRadius(SNEAKER_ID, EntityFilter.NOT_SNEAKING, false);
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
        StepHost first = walker(WALKER_ID);
        StepHost second = walker(OTHER_WALKER_ID);
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
        StepHost walker = walker(WALKER_ID);
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
    void eightShredsOnAMovingEntityDecrementTheStackOnce() throws IOException {
        AtomicInteger stacks = new AtomicInteger(2);
        StepHost walker = walker(WALKER_ID);
        StepHost host = marker(stacks, List.of(walker));
        ProgramBehavior program = ProgramBehavior.forHost(crystalProgram(), HostKind.MARKER);

        tick(program, host, WALKING_TICKS_FOR_A_BLOB - 1);
        assertEquals(2, stacks.get());
        verify(walker, times(CHARGES_PER_BLOB - 1)).damageTarget(SHRED_DAMAGE, DamageKind.CACTUS, true);

        program.tick(host);

        verify(walker, times(CHARGES_PER_BLOB)).damageTarget(SHRED_DAMAGE, DamageKind.CACTUS, true);
        verify(walker, times(CHARGES_PER_BLOB)).setTargetHurtCooldown(1);
        verify(host, times(1)).decrementStack();
        assertEquals(1, stacks.get());
    }

    @Test
    void aSprintingPlayerIsShreddedEveryTickTwiceAsOftenAsAWalker() throws IOException {
        StepHost walker = walker(WALKER_ID);
        StepHost sprinter = entityInRadius(OTHER_WALKER_ID, null, true);
        StepHost host = marker(new AtomicInteger(2), List.of(walker, sprinter));
        ProgramBehavior program = ProgramBehavior.forHost(crystalProgram(), HostKind.MARKER);

        tick(program, host, SPRINT_WINDOW);

        verify(sprinter, times(SPRINT_WINDOW)).damageTarget(SHRED_DAMAGE, DamageKind.CACTUS, true);
        verify(walker, times(SPRINT_WINDOW / 2)).damageTarget(SHRED_DAMAGE, DamageKind.CACTUS, true);
    }

    @Test
    void aStandingEntityIsNotShredded() throws IOException {
        AtomicInteger stacks = new AtomicInteger(1);
        StepHost standing = entityInRadius(WALKER_ID, EntityFilter.MOVING, false);
        StepHost host = marker(stacks, List.of(standing));
        ProgramBehavior program = ProgramBehavior.forHost(crystalProgram(), HostKind.MARKER);

        tick(program, host, IDLE_TICKS);

        verify(standing, never()).damageTarget(anyFloat(), any(), anyBoolean());
        assertEquals(1, stacks.get());
        assertTrue(program.isActive());
    }

    @Test
    void theCloudExpandsThenContractsOnceItsLastBlobIsSpent() throws IOException {
        AtomicInteger stacks = new AtomicInteger(1);
        StepHost host = marker(stacks, List.of(walker(WALKER_ID)));
        FieldEffectState state = host.fieldEffect();
        ProgramBehavior program = ProgramBehavior.forHost(crystalProgram(), HostKind.MARKER);

        tick(program, host, CLOUD_ANIMATION_TICKS / 2);
        assertEquals(0.5f, state.radiusFraction(), FRACTION_TOLERANCE);
        tick(program, host, WALKING_TICKS_FOR_A_BLOB - CLOUD_ANIMATION_TICKS / 2);
        assertEquals(0, stacks.get());
        assertEquals(1f, state.radiusFraction(), FRACTION_TOLERANCE);
        assertEquals(0f, state.density(), FRACTION_TOLERANCE);

        program.tick(host);
        assertEquals(1f - 1f / CLOUD_ANIMATION_TICKS, state.radiusFraction(), FRACTION_TOLERANCE);

        tick(program, host, CLOUD_ANIMATION_TICKS - 1);
        assertTrue(program.isActive());
        assertEquals(0f, state.radiusFraction(), FRACTION_TOLERANCE);
        program.tick(host);
        assertFalse(program.isActive());
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
    void theIntervalIsReadOnTheSelectedEntitySoAMarkerVariableThereRefusesAtLoad() {
        List<Step> steps = List.of(new FieldEffectStep(Expr.literal(3), List.of(), Expr.literal(0),
                Expr.parse("stacks").getOrThrow(), Expr.literal(1), Expr.literal(0), Expr.literal(1),
                FieldTiming.INSTANT, List.of(new DamageStep(Expr.literal(1), DamageKind.CACTUS)), List.of()));

        ProgramLoadException refusal = assertThrows(ProgramLoadException.class,
                () -> ProgramBehavior.forHost(steps, HostKind.MARKER));

        assertTrue(refusal.getMessage().contains("stacks"), refusal.getMessage());
        assertTrue(refusal.getMessage().contains("struck entity"), refusal.getMessage());
    }

    @Test
    void aMarkerOnlyStepInTheStrikeBodyRefusesAtLoadSinceItRunsOnTheStruckEntity() {
        List<Step> steps = List.of(new FieldEffectStep(Expr.literal(3), List.of(), Expr.literal(0),
                Expr.literal(1), Expr.literal(1), Expr.literal(0), Expr.literal(1), FieldTiming.INSTANT,
                List.of(new ExplodeStep(Expr.literal(2), ExplosionMode.NONE),
                        new WaitStep(Expr.literal(2))),
                List.of()));

        ProgramLoadException refusal = assertThrows(ProgramLoadException.class,
                () -> ProgramBehavior.forHost(steps, HostKind.MARKER));

        assertTrue(refusal.getMessage().contains("wait"), refusal.getMessage());
        assertTrue(refusal.getMessage().contains("struck entity"), refusal.getMessage());
    }
}
