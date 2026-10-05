package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.PhasedState;
import net.minecraft.util.ARGB;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BlackHolePhases' hole body radius carries the nether marker's constant
 * pulse, and the hole draws nothing through the gather that precedes expand
 * until its startup ramp, which fades it in to meet expand's first frame.
 */
class BlackHolePhasesTest {

    private static final float TOLERANCE = 1e-4f;
    /** A game time far from zero, as a live level's clock reads. */
    private static final float GAME_TIME = 48_213f;
    private static final float FULL_RADIUS = 6f;
    private static final int SAMPLES = 40;
    private static final int GATHER_TICKS = 20;
    private static final int EXPAND_TICKS = 15;
    private static final int FULL_ALPHA = 0xFF;
    private static final int RAMP_STEPS_PER_TICK = 4;
    private static final float RAMP_EDGE = 1e-4f;
    private static final float HALF_RAMP = 0.5f;

    @Test
    void pulseReturnsToItsStartAfterOnePeriod() {
        assertEquals(BlackHolePhases.holePulse(GAME_TIME),
                BlackHolePhases.holePulse(GAME_TIME + BlackHolePhases.HOLE_PULSE_PERIOD), TOLERANCE);
    }

    @Test
    void pulseStaysWithinItsAmplitudeAndReachesIt() {
        float lowest = Float.MAX_VALUE;
        float highest = -Float.MAX_VALUE;
        for (int i = 0; i < SAMPLES; i++) {
            float pulse = BlackHolePhases.holePulse(GAME_TIME + i * BlackHolePhases.HOLE_PULSE_PERIOD / SAMPLES);
            assertTrue(Math.abs(pulse - 1f) <= BlackHolePhases.HOLE_PULSE_AMPLITUDE + TOLERANCE,
                    "pulse " + pulse + " past its amplitude");
            lowest = Math.min(lowest, pulse);
            highest = Math.max(highest, pulse);
        }
        assertEquals(2 * BlackHolePhases.HOLE_PULSE_AMPLITUDE, highest - lowest, 0.01f);
    }

    @Test
    void holdPhaseRadiusChangesAQuarterPeriodLater() {
        float now = BlackHolePhases.bodyRadius(FULL_RADIUS, 1f, GAME_TIME);
        float later = BlackHolePhases.bodyRadius(FULL_RADIUS, 1f,
                GAME_TIME + BlackHolePhases.HOLE_PULSE_PERIOD / 4);
        assertNotEquals(now, later, TOLERANCE);
    }

    @Test
    void pulseRidesThePhaseScale() {
        float scale = 0.5f;
        assertEquals(FULL_RADIUS * scale * BlackHolePhases.holePulse(GAME_TIME),
                BlackHolePhases.bodyRadius(FULL_RADIUS, scale, GAME_TIME), TOLERANCE);
    }

    @Test
    void holeDrawsNothingThroughTheGatherBeforeItsRamp() {
        PhasedState gather = gatherAt(0);
        for (int tick = 0; tick < GATHER_TICKS - StartupRamp.RAMP_TICKS; tick++) {
            assertTrue(BlackHolePhases.holeRamp(gather, 0.5f).isEmpty(), "the hole draws at gather tick " + tick);
            assertEquals(0f, BlackHolePhases.visibleScale(gather), 0f);
            assertEquals(0f, BlackHolePhases.diskExpansionScale(gather), 0f);
            gather.countTick();
        }
        assertTrue(BlackHolePhases.holeRamp(gatherAt(GATHER_TICKS - StartupRamp.RAMP_TICKS), 0f).isEmpty());
    }

    @Test
    void gatherNotYetEnteredDrawsNothing() {
        PhasedState unentered = new PhasedState();
        unentered.advance("gather");
        assertTrue(BlackHolePhases.holeRamp(unentered, 0.5f).isEmpty());
    }

    @Test
    void rampStartsBelowTheFloorAndTransparent() {
        float ramp = (float) BlackHolePhases.holeRamp(gatherAt(GATHER_TICKS - StartupRamp.RAMP_TICKS), RAMP_EDGE)
                .orElseThrow();
        assertTrue(BlackHolePhases.rampedBodyRadius(FULL_RADIUS, 0f, GAME_TIME, ramp)
                < BlackHolePhases.HOLE_MIN_RADIUS, "the hole starts at its floor");
        assertEquals(0, StartupRamp.alpha(ramp));
    }

    @Test
    void rampEndsOnExpandsFirstFrame() {
        PhasedState expand = new PhasedState();
        expand.enter("expand", EXPAND_TICKS);
        float expandRamp = (float) BlackHolePhases.holeRamp(expand, 0f).orElseThrow();
        float expandRadius = BlackHolePhases.rampedBodyRadius(FULL_RADIUS, BlackHolePhases.visibleScale(expand),
                GAME_TIME, expandRamp);
        float rampEnd = (float) BlackHolePhases.holeRamp(gatherAt(GATHER_TICKS - 1), 1f).orElseThrow();
        assertEquals(expandRadius, BlackHolePhases.rampedBodyRadius(FULL_RADIUS, 0f, GAME_TIME, rampEnd),
                TOLERANCE);
        assertEquals(StartupRamp.alpha(expandRamp), StartupRamp.alpha(rampEnd));
        assertEquals(FULL_ALPHA, StartupRamp.alpha(rampEnd));
    }

    @Test
    void rampRisesThroughTheGathersLastTicks() {
        float previousRadius = -1f;
        int previousAlpha = -1;
        for (int tick = GATHER_TICKS - StartupRamp.RAMP_TICKS; tick < GATHER_TICKS; tick++) {
            for (int step = 1; step <= RAMP_STEPS_PER_TICK; step++) {
                float partial = step / (float) RAMP_STEPS_PER_TICK;
                float ramp = (float) BlackHolePhases.holeRamp(gatherAt(tick), partial).orElseThrow();
                float radius = BlackHolePhases.rampedBodyRadius(FULL_RADIUS, 0f, GAME_TIME, ramp);
                int alpha = StartupRamp.alpha(ramp);
                assertTrue(radius > previousRadius, "the hole shrinks at gather tick " + tick + " + " + partial);
                assertTrue(alpha >= previousAlpha, "the hole fades out at gather tick " + tick + " + " + partial);
                previousRadius = radius;
                previousAlpha = alpha;
            }
        }
    }

    @Test
    void rampFrameColorsCarryTheRampsOpacity() {
        int alpha = StartupRamp.alpha(HALF_RAMP);
        assertEquals(alpha, ARGB.alpha(NetherSphereVisual.packBlackholeColor(0f, 0.3f, 0.2f, alpha)));
        assertEquals(alpha, ARGB.alpha(NetherDiscMesh.packDiskColor(10, 20, 30, alpha)));
        assertNotEquals(FULL_ALPHA, alpha);
    }

    /**
     * @param ticks the gather ticks already counted
     * @return a gather cursor that many ticks in
     */
    private static PhasedState gatherAt(int ticks) {
        PhasedState gather = new PhasedState();
        gather.enter("gather", GATHER_TICKS);
        for (int i = 0; i < ticks; i++) {
            gather.countTick();
        }
        return gather;
    }
}
