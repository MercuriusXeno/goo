package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.network.HeldSweepPayload;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scry, Glitter and Magnet, decoded from their shipped JSON, each sweep
 * through the one held-sweep payload; and a held sweep's pulses overlap:
 * the hold's eleventh tick starts a second pulse while the first still
 * travels, each live pulse sending its own payload.
 * decision held-sweeps-share-one-fast-shell-visual
 */
class HeldSweepStepTest {

    private static final List<String> HELD_SWEEPS = List.of("astral_scry", "crystal_glitter", "metal_magnet");
    private static final long PRESSED_AT = 1000;
    private static final double BLOCKS_A_TICK = 2.4;
    private static final double EXACT = 1e-5;

    @Test
    void scryGlitterAndMagnetDrawThroughOneSweepVisual() {
        HeldSweepPulses.Pulse pulse = new HeldSweepPulses.Pulse(1, Vec3.ZERO, PRESSED_AT);
        Set<Class<?>> visuals = HELD_SWEEPS.stream()
                .map(name -> sweepOf(name).sweepOf(pulse, PRESSED_AT).getClass()).collect(Collectors.toSet());

        assertEquals(Set.of(HeldSweepPayload.class), visuals, "one visual draws all three");
    }

    /** Scry's fronts paint the mobs they cross; Glitter's and Magnet's, which find ores, do not. */
    @Test
    void scrysFrontsPaintMobsAndGlittersAndMagnetsDoNot() {
        HeldSweepPulses.Pulse pulse = new HeldSweepPulses.Pulse(1, Vec3.ZERO, PRESSED_AT);

        assertTrue(sweepOf("astral_scry").sweepOf(pulse, PRESSED_AT).paintsMobs(), "scry");
        assertTrue(!sweepOf("crystal_glitter").sweepOf(pulse, PRESSED_AT).paintsMobs(), "glitter");
        assertTrue(!sweepOf("metal_magnet").sweepOf(pulse, PRESSED_AT).paintsMobs(), "magnet");
    }

    @Test
    void theHoldsEleventhTickSendsTwoFrontsEachWithItsOwnPulseAndRadius() {
        HeldSweepStep scry = sweepOf("astral_scry");
        List<HeldSweepPulses.Pulse> live = holdFor(scry, new HeldSweepPulses(), 11);

        List<HeldSweepPayload> sent = live.stream().map(pulse -> scry.sweepOf(pulse, PRESSED_AT + 10)).toList();

        assertEquals(2, sent.size(), "two fronts stand at once");
        assertEquals(2, sent.stream().map(HeldSweepPayload::pulse).distinct().count(), "each names its own pulse");
        assertEquals(11 * BLOCKS_A_TICK, sent.get(0).radius(), EXACT, "the first front, eleven ticks out");
        assertEquals(BLOCKS_A_TICK, sent.get(1).radius(), EXACT, "the second front, just leaving");
    }

    @Test
    void aPulseLeavesOnceItsFrontHasReachedItsRange() {
        HeldSweepStep scry = sweepOf("astral_scry");

        List<HeldSweepPulses.Pulse> live = holdFor(scry, new HeldSweepPulses(), scry.pace().ticksToRange() + 1);

        assertTrue(live.stream().noneMatch(pulse -> pulse.startedAt() == PRESSED_AT), "the first front has landed");
        assertTrue(!live.isEmpty(), "the later fronts still travel");
    }

    /**
     * Holds a sweep from its press for a number of ticks, one tick of game time each.
     */
    private static List<HeldSweepPulses.Pulse> holdFor(HeldSweepStep sweep, HeldSweepPulses pulses, int ticks) {
        UUID caster = new UUID(0, 1);
        List<HeldSweepPulses.Pulse> live = List.of();
        for (int held = 1; held <= ticks; held++) {
            live = pulses.advance(caster, Vec3.ZERO, held, PRESSED_AT + held - 1, sweep.pace());
        }
        return live;
    }

    private static HeldSweepStep sweepOf(String name) {
        return AbilityJson.decode(name).behaviors().stream()
                .filter(HeldSweepStep.class::isInstance).map(HeldSweepStep.class::cast).findFirst().orElseThrow();
    }
}
