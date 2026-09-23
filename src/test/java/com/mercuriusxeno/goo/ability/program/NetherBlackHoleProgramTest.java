package com.mercuriusxeno.goo.ability.program;

import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The nether_black_hole program, decoded from its JSON and loaded for the
 * marker host, runs its phases in order on a Mockito marker host holding
 * one stack, a real phase cursor and one living entity in every sphere it
 * scans: on the fuse tick it blinds and darkens the entity and plays its
 * sound; it pulls from three times its radius through expand and hold;
 * leaving expand it consumes its sphere's valued blocks once and halves
 * the entity's health once; it drops the consumed goo once, the tick
 * after contract ends, and the program ends there.
 */
class NetherBlackHoleProgramTest {

    private static final String NETHER_BLACK_HOLE = "/data/goo/goo_abilities/nether_black_hole.json";
    private static final int STACKS = 1;
    /** One stack's blast radius, {@code 1 + 2 * stacks}. */
    private static final int RADIUS = 3;
    private static final int PULL_RADIUS = 3 * RADIUS;
    private static final double PULL_SPEED = 0.15;
    private static final int EXPAND_TICKS = 15;
    private static final int HOLD_TICKS = 15;
    private static final int CONTRACT_TICKS = 30;
    private static final int LAST_CONTRACT_TICK = EXPAND_TICKS + HOLD_TICKS + CONTRACT_TICKS;
    private static final int POPPING_TICK = LAST_CONTRACT_TICK + 1;
    private static final float ENTITY_HEALTH = 10f;
    /** Blindness and darkness outlast expand, hold and contract by ten ticks. */
    private static final int DIMMING_TICKS = 70;
    private static final Identifier BLINDNESS = Identifier.parse("minecraft:blindness");
    private static final Identifier DARKNESS = Identifier.parse("minecraft:darkness");
    private static final Identifier BLACK_HOLE_SOUND = Identifier.parse("goo:effects.black_hole");
    private static final float PROGRESS_TOLERANCE = 1e-6f;

    /** The ticks, counted from one on the fuse tick, on which each host act ran. */
    private final Map<String, List<Integer>> actTicks = new LinkedHashMap<>();
    /** The phase the cursor names after each tick, index zero after the first. */
    private final List<String> phaseAfterTick = new ArrayList<>();
    private final PhasedState state = new PhasedState();
    private int tick;
    private ProgramBehavior runner;
    private StepHost host;

    private static List<Step> program() throws IOException {
        try (InputStream in = NetherBlackHoleProgramTest.class.getResourceAsStream(NETHER_BLACK_HOLE)) {
            assertNotNull(in, "Classpath holds no " + NETHER_BLACK_HOLE);
            Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
            AbilityDefinition def = AbilityDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader))
                    .getOrThrow(IllegalStateException::new);
            return def.behaviors().get(0).steps();
        }
    }

    private void record(String act) {
        actTicks.computeIfAbsent(act, key -> new ArrayList<>()).add(tick);
    }

    private List<Integer> ticksOf(String act) {
        return actTicks.getOrDefault(act, List.of());
    }

    private static List<Integer> ticks(int first, int last) {
        return IntStream.rangeClosed(first, last).boxed().toList();
    }

    /**
     * A living entity the marker's scan binds as a target host, recording
     * each potion by effect, amplifier and scan radius, and each hit.
     *
     * @param scanRadius the radius of the scan that selected it
     * @return the target host
     */
    private StepHost entityInSphere(double scanRadius) {
        StepHost target = mock(StepHost.class);
        when(target.kind()).thenReturn(HostKind.ENTITY);
        when(target.read(anyString())).thenReturn(OptionalDouble.empty());
        when(target.read(HostVariables.HEALTH)).thenReturn(OptionalDouble.of(ENTITY_HEALTH));
        doAnswer(inv -> {
            record("potion " + inv.getArgument(0) + " x" + inv.getArgument(2) + " for " + inv.getArgument(1)
                    + " within " + scanRadius + " visible " + inv.getArgument(3));
            return null;
        }).when(target).applyPotion(any(), anyInt(), anyInt(), anyBoolean());
        doAnswer(inv -> {
            record("damage " + inv.getArgument(0) + " " + inv.getArgument(1) + " within " + scanRadius);
            return null;
        }).when(target).damageTarget(anyFloat(), any(), anyBoolean());
        return target;
    }

    /**
     * A marker host holding one stack and a real phase cursor, recording
     * each act it is asked for.
     *
     * @return the marker host
     */
    private StepHost marker() {
        StepHost host = mock(StepHost.class);
        when(host.kind()).thenReturn(HostKind.MARKER);
        when(host.phased()).thenReturn(state);
        when(host.read(anyString())).thenReturn(OptionalDouble.empty());
        when(host.read(HostVariables.STACKS)).thenReturn(OptionalDouble.of(STACKS));
        doAnswer(inv -> {
            Consumer<StepHost> body = inv.getArgument(3);
            body.accept(entityInSphere(inv.getArgument(1)));
            return null;
        }).when(host).forEachEntityWithin(any(), anyDouble(), anySet(), any());
        doAnswer(inv -> {
            record("pull within " + inv.getArgument(0) + " at " + inv.getArgument(1));
            return null;
        }).when(host).pullEntitiesWithin(anyDouble(), anyDouble());
        doAnswer(inv -> {
            record("consume within " + inv.getArgument(0));
            return null;
        }).when(host).consumeValuedBlocks(anyInt());
        doAnswer(inv -> {
            record("drop consumed");
            return null;
        }).when(host).dropConsumedGoo();
        doAnswer(inv -> {
            SoundCue cue = inv.getArgument(1);
            record("sound " + cue.sound() + " at volume " + cue.volume());
            return null;
        }).when(host).playSound(any(), any());
        doAnswer(inv -> {
            ParticleBurst burst = inv.getArgument(1);
            record("particles " + burst.particle() + " x" + burst.count() + " spread " + burst.spreadAlong());
            return null;
        }).when(host).spawnParticles(any(), any());
        return host;
    }

    @BeforeEach
    void loadTheProgram() throws IOException {
        runner = ProgramBehavior.forHost(program(), HostKind.MARKER);
        host = marker();
    }

    /**
     * Ticks the program once, the first call standing for the fuse tick.
     */
    private void tickOnce() {
        tick++;
        runner.tick(host);
        phaseAfterTick.add(state.name());
    }

    private void runToTheEnd() {
        while (runner.isActive() && tick <= POPPING_TICK) {
            tickOnce();
        }
    }

    @Test
    void phasesRunExpandHoldContractThenPopAndTheProgramEnds() {
        runToTheEnd();

        assertEquals(POPPING_TICK, tick);
        assertFalse(runner.isActive());
        assertFalse(state.isRunning());
        assertEquals("expand", phaseAfterTick.get(EXPAND_TICKS - 2));
        assertEquals("hold", phaseAfterTick.get(EXPAND_TICKS - 1));
        assertEquals("hold", phaseAfterTick.get(EXPAND_TICKS + HOLD_TICKS - 2));
        assertEquals("contract", phaseAfterTick.get(EXPAND_TICKS + HOLD_TICKS - 1));
        assertEquals("contract", phaseAfterTick.get(LAST_CONTRACT_TICK - 2));
        assertEquals("popping", phaseAfterTick.get(LAST_CONTRACT_TICK - 1));
    }

    @Test
    void theFuseTickBlindsDarkensInTwoBandsAndPlaysTheSound() {
        tickOnce();

        assertEquals(List.of(1), ticksOf("potion " + BLINDNESS + " x0 for " + DIMMING_TICKS
                + " within " + (double) RADIUS + " visible false"));
        assertEquals(List.of(1), ticksOf("potion " + DARKNESS + " x1 for " + DIMMING_TICKS
                + " within " + (double) PULL_RADIUS + " visible false"));
        assertEquals(List.of(1), ticksOf("potion " + DARKNESS + " x2 for " + DIMMING_TICKS
                + " within " + (double) RADIUS + " visible false"));
        assertEquals(List.of(1), ticksOf("sound " + BLACK_HOLE_SOUND + " at volume 6.0"));
        runToTheEnd();
        assertEquals(List.of(1), ticksOf("sound " + BLACK_HOLE_SOUND + " at volume 6.0"));
    }

    @Test
    void pullRunsEveryTickOfExpandAndHoldFromThreeTimesTheRadius() {
        runToTheEnd();

        assertEquals(ticks(1, EXPAND_TICKS + HOLD_TICKS),
                ticksOf("pull within " + (double) PULL_RADIUS + " at " + PULL_SPEED));
    }

    @Test
    void soulParticlesRunEveryTickOfExpandHoldAndContract() {
        runToTheEnd();

        assertEquals(ticks(1, LAST_CONTRACT_TICK),
                ticksOf("particles minecraft:soul x" + (1 + STACKS) + " spread " + 0.6 * RADIUS));
    }

    @Test
    void leavingExpandConsumesTheSphereOnceAndHalvesTheHealthInsideOnce() {
        runToTheEnd();

        assertEquals(List.of(EXPAND_TICKS), ticksOf("consume within " + RADIUS));
        assertEquals(List.of(EXPAND_TICKS), ticksOf("damage " + ENTITY_HEALTH / 2 + " "
                + DamageKind.MAGIC + " within " + (double) RADIUS));
    }

    @Test
    void theConsumedGooDropsOnceTheTickAfterContractEnds() {
        runToTheEnd();

        assertEquals(List.of(POPPING_TICK), ticksOf("drop consumed"));
    }

    @Test
    void theCursorExposesPhaseProgressAndRadiusToTheRenderer() {
        IntStream.range(0, EXPAND_TICKS / 3).forEach(i -> tickOnce());

        assertTrue(state.isRunning());
        assertEquals("expand", state.name());
        assertEquals(1f / 3, state.progress(), PROGRESS_TOLERANCE);
        assertEquals(RADIUS, state.radius());
        IntStream.range(EXPAND_TICKS / 3, EXPAND_TICKS).forEach(i -> tickOnce());
        assertEquals("hold", state.name());
        assertEquals(0f, state.progress());
    }
}
