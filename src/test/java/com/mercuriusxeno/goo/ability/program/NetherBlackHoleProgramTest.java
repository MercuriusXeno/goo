package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.AbilityJson;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The nether_black_hole program, decoded from its JSON and loaded for the
 * marker host, runs its phases in order on a Mockito marker host holding
 * one stack and a real phase cursor, recording the radius of every sphere
 * it scans: it gathers first, running no act while nether's inward rush
 * plays on the client; on expand's first tick it scans its radius twice and three times its
 * radius once, for the blindness and the two darkness bands, and plays its
 * sound; it pulls mobs and items from three times its radius through expand and hold;
 * leaving expand it takes its sphere's blocks into the hoard once and scans
 * its radius once for the lethal hit; it leaves the hoard as a compression
 * sphere once, the tick after contract ends, and the program ends there. The potions and
 * the hit act on each scanned entity itself, which no unit test can build,
 * so the black-hole gametest proves them (decision step-tick-holds-effect).
 */
class NetherBlackHoleProgramTest {

    /** The soul particles the program names each tick. */
    private static final int SOUL_COUNT = 2;
    /** The spread the program names for its soul particles. */
    private static final double SOUL_SPREAD = 1.8;
    /** One stack's blast radius, {@code 2 + stacks}. */
    private static final int RADIUS = 3;
    private static final int PULL_RADIUS = 3 * RADIUS;
    private static final double PULL_SPEED = 0.15;
    private static final int GATHER_TICKS = 20;
    private static final int EXPAND_TICKS = 15;
    /** The first tick of expand, the tick after the gather ends. */
    private static final int EXPAND_START = GATHER_TICKS + 1;
    /** The last tick of expand. */
    private static final int EXPAND_END = GATHER_TICKS + EXPAND_TICKS;
    private static final int HOLD_TICKS = 15;
    private static final int CONTRACT_TICKS = 30;
    private static final int LAST_CONTRACT_TICK = GATHER_TICKS + EXPAND_TICKS + HOLD_TICKS + CONTRACT_TICKS;
    private static final int POPPING_TICK = LAST_CONTRACT_TICK + 1;
    private static final Identifier BLACK_HOLE_SOUND = Identifier.parse("goo:effects.black_hole");
    private static final float PROGRESS_TOLERANCE = 1e-6f;

    /** The ticks, counted from one on the splat tick, on which each host act ran. */
    private final Map<String, List<Integer>> actTicks = new LinkedHashMap<>();
    /** The phase the cursor names after each tick, index zero after the first. */
    private final List<String> phaseAfterTick = new ArrayList<>();
    private final PhasedState state = new PhasedState();
    private int tick;
    private ProgramBehavior runner;
    private MarkerHost host;

    private static List<Step> program() {
        return LingerStep.bodyOf(AbilityJson.decode("nether_black_hole").behaviors()).orElseThrow();
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
     * A marker host holding a real phase cursor, recording
     * each act it is asked for.
     *
     * @return the marker host
     */
    private MarkerHost marker() {
        MarkerHost host = mock(MarkerHost.class);
        when(host.kind()).thenReturn(HostKind.MARKER);
        when(host.phased()).thenReturn(state);
        when(host.read(anyString())).thenReturn(OptionalDouble.empty());
        // black-hole-leaves-a-compression-sphere: the hole is cast at the radius its JSON cost buys
        when(host.read(HostVariables.SIZE)).thenReturn(OptionalDouble.of(RADIUS));
        doAnswer(inv -> {
            // the once-a-second cut scans only what its last cut no longer holds immune
            Set<EntityFilter> where = inv.getArgument(2);
            record((where.contains(EntityFilter.VULNERABLE) ? "cut within " : "scan within ") + inv.getArgument(1));
            return null;
        }).when(host).forEachEntityWithin(any(), anyDouble(), anySet(), any());
        doAnswer(inv -> {
            record("pull within " + inv.getArgument(0) + " at " + inv.getArgument(1));
            return null;
        }).when(host).pullEntitiesWithin(anyDouble(), anyDouble());
        doAnswer(inv -> {
            record("consume within " + inv.getArgument(0));
            return null;
        }).when(host).hoardBlocks(anyInt());
        doAnswer(inv -> {
            record("pull items within " + inv.getArgument(0) + " at " + inv.getArgument(1));
            return null;
        }).when(host).pullItemsIntoHoard(anyDouble(), anyDouble());
        doAnswer(inv -> {
            record("drop sphere");
            return null;
        }).when(host).dropSphere();
        doAnswer(inv -> {
            SoundCue cue = inv.getArgument(0);
            record("sound " + cue.sound() + " at volume " + cue.volume());
            return null;
        }).when(host).playSound(any());
        doAnswer(inv -> {
            ParticleBurst burst = inv.getArgument(0);
            record("particles " + burst.particle() + " x" + burst.count() + " spread " + burst.spreadAlong());
            return null;
        }).when(host).spawnParticles(any());
        return host;
    }

    @BeforeEach
    void loadTheProgram() throws IOException {
        runner = ProgramBehavior.forHost(program(), HostKind.MARKER);
        host = marker();
    }

    /**
     * Ticks the program once, the first call standing for the splat tick.
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
    void phasesRunGatherExpandHoldContractThenPopAndTheProgramEnds() {
        runToTheEnd();

        assertEquals(POPPING_TICK, tick);
        assertFalse(runner.isActive());
        assertFalse(state.isRunning());
        assertEquals("gather", phaseAfterTick.get(GATHER_TICKS - 2));
        assertEquals("expand", phaseAfterTick.get(GATHER_TICKS - 1));
        assertEquals("expand", phaseAfterTick.get(EXPAND_END - 2));
        assertEquals("hold", phaseAfterTick.get(EXPAND_END - 1));
        assertEquals("hold", phaseAfterTick.get(EXPAND_END + HOLD_TICKS - 2));
        assertEquals("contract", phaseAfterTick.get(EXPAND_END + HOLD_TICKS - 1));
        assertEquals("contract", phaseAfterTick.get(LAST_CONTRACT_TICK - 2));
        assertEquals("popping", phaseAfterTick.get(LAST_CONTRACT_TICK - 1));
    }

    @Test
    void theGatherRunsNoActButTheCutAndConsumesNoBlocks() {
        IntStream.range(0, GATHER_TICKS).forEach(i -> tickOnce());

        assertEquals(Set.of("cut within " + (double) RADIUS), actTicks.keySet());
    }

    /**
     * The operator's ruling on decision black-hole-leaves-a-compression-sphere:
     * everything inside is cut once a second for as long as the hole stands;
     * the cut's scan runs every tick, keeping only what its last cut no
     * longer holds immune, so each thing inside is cut once a second.
     */
    @Test
    void theCutScansTheSphereEveryTickTheHoleStands() {
        runToTheEnd();

        assertEquals(ticks(1, LAST_CONTRACT_TICK), ticksOf("cut within " + (double) RADIUS));
    }

    @Test
    void expandsFirstTickScansForBlindnessAndBothDarknessBandsAndPlaysTheSound() {
        IntStream.range(0, EXPAND_START).forEach(i -> tickOnce());

        assertEquals(List.of(EXPAND_START, EXPAND_START), ticksOf("scan within " + (double) RADIUS));
        assertEquals(List.of(EXPAND_START), ticksOf("scan within " + (double) PULL_RADIUS));
        assertEquals(List.of(EXPAND_START), ticksOf("sound " + BLACK_HOLE_SOUND + " at volume 6.0"));
        runToTheEnd();
        assertEquals(List.of(EXPAND_START), ticksOf("sound " + BLACK_HOLE_SOUND + " at volume 6.0"));
    }

    @Test
    void pullDrawsMobsAndItemsEveryTickOfExpandAndHoldFromThreeTimesTheRadius() {
        runToTheEnd();

        assertEquals(ticks(EXPAND_START, EXPAND_END + HOLD_TICKS),
                ticksOf("pull within " + (double) PULL_RADIUS + " at " + PULL_SPEED));
        assertEquals(ticks(EXPAND_START, EXPAND_END + HOLD_TICKS),
                ticksOf("pull items within " + (double) PULL_RADIUS + " at " + PULL_SPEED));
    }

    @Test
    void soulParticlesRunEveryTickOfExpandHoldAndContract() {
        runToTheEnd();

        assertEquals(ticks(EXPAND_START, LAST_CONTRACT_TICK),
                ticksOf("particles minecraft:soul x" + SOUL_COUNT + " spread " + SOUL_SPREAD));
    }

    @Test
    void leavingExpandHoardsTheSphereOnceAndScansItOnceForTheLethalHit() {
        runToTheEnd();

        assertEquals(List.of(EXPAND_END), ticksOf("consume within " + RADIUS));
        assertEquals(List.of(EXPAND_START, EXPAND_START, EXPAND_END), ticksOf("scan within " + (double) RADIUS));
    }

    @Test
    void theSphereDropsOnceTheTickAfterContractEnds() {
        runToTheEnd();

        assertEquals(List.of(POPPING_TICK), ticksOf("drop sphere"));
    }

    @Test
    void theCursorExposesPhaseProgressAndTheStepItsRadiusToTheRenderer() {
        IntStream.range(0, GATHER_TICKS + EXPAND_TICKS / 3).forEach(i -> tickOnce());

        assertTrue(state.isRunning());
        assertEquals("expand", state.name());
        assertEquals(1f / 3, state.progress(), PROGRESS_TOLERANCE);
        assertEquals(RADIUS, ((PhasedStep) program().getFirst()).radius().evaluateFloat(host));
        IntStream.range(EXPAND_TICKS / 3, EXPAND_TICKS).forEach(i -> tickOnce());
        assertEquals("hold", state.name());
        assertEquals(0f, state.progress());
    }
}
