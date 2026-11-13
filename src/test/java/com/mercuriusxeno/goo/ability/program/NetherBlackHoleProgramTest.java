package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.AbilityJson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The void_dark_star program, decoded from its JSON and loaded for the
 * marker host, runs its phases in order on a Mockito marker host holding a
 * real phase cursor; each phase holds the steps of its part of the hole, its
 * renderer radius is the cast size.
 * The potions and the hit act on each scanned entity itself, which no unit
 * test can build, so the black-hole gametest proves them (decision step-tick-holds-effect).
 */
class NetherBlackHoleProgramTest {

    /** The size the hole is cast at. */
    private static final int RADIUS = 3;
    /** A bound on the run far past any tuning of the hole's phases, so a program that never ends fails. */
    private static final int MOST_TICKS = 10_000;
    /** Per phase, the step types its enter, tick and leave lists hold. */
    private static final Map<String, List<List<String>>> EXPECTED_PHASE_STEPS = Map.of(
            "gather", List.of(List.of(), List.of("entities"), List.of()),
            "expand", List.of(List.of("entities", "entities", "entities", "sound"),
                    List.of("entities", "particles", "pull"), List.of("consume_blocks", "entities")),
            "hold", List.of(List.of(), List.of("entities", "particles", "pull"), List.of()),
            "contract", List.of(List.of(), List.of("entities", "particles"), List.of()),
            "popping", List.of(List.of("drop_sphere"), List.of(), List.of()));

    /** The phase the cursor names after each tick, index zero after the first. */
    private final List<String> phaseAfterTick = new ArrayList<>();
    private final PhasedState state = new PhasedState();
    private int tick;
    private ProgramBehavior runner;
    private MarkerHost host;

    private static List<Step> program() {
        return LingerStep.bodyOf(AbilityJson.decode("void_dark_star").behaviors()).orElseThrow();
    }

    /**
     * A marker host holding a real phase cursor, the hole cast at RADIUS.
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
        while (runner.isActive() && tick <= MOST_TICKS) {
            tickOnce();
        }
    }
    @Test
    void phasesRunGatherExpandHoldContractThenPopAndTheProgramEnds() {
        runToTheEnd();

        assertFalse(runner.isActive());
        assertFalse(state.isRunning());
        assertEquals(List.of("gather", "expand", "hold", "contract", "popping"),
                phaseAfterTick.stream().filter(name -> !name.isEmpty()).distinct().toList());
    }

    /**
     * Each phase's enter, tick and leave lists hold the steps the hole runs: the
     * cut through every timed phase, the blind and dark scans and the sound on
     * expand's entry, the pull through expand and hold, the souls through
     * contract, the hoard and the lethal scan leaving expand, the sphere dropped
     * on popping (decision black-hole-leaves-a-compression-sphere).
     */
    @Test
    void eachPhaseRunsTheStepsOfItsPartOfTheHole() {
        Map<String, List<List<String>>> byPhase = new LinkedHashMap<>();
        for (StepPhase phase : ((PhasedStep) program().getFirst()).phases()) {
            byPhase.put(phase.name(), List.of(typesOf(phase.enter()), typesOf(phase.tick()), typesOf(phase.leave())));
        }

        assertEquals(EXPECTED_PHASE_STEPS, byPhase);
    }

    private static List<String> typesOf(List<Step> steps) {
        return steps.stream().map(step -> step.type().name()).toList();
    }

    @Test
    void theStepsRadiusIsTheCastSizeTheRendererDraws() {
        assertEquals(RADIUS, ((PhasedStep) program().getFirst()).radius().evaluateFloat(host));
    }
}
