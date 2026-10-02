package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.OptionalDouble;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DomeRamp runs a burnout dome's startup frames over the fuse's last ticks:
 * when a marker draws them, how far they have run, and the radius and
 * opacity they draw at.
 */
class DomeRampTest {

    private static final float TOLERANCE = 1e-5f;
    private static final String TUNNEL_ABILITY = "goo:rock_tunnel";
    private static final String DOME_ABILITY = "goo:blaze_burst";

    @Nested
    class WhenTheMarkerDrawsTheRamp {

        @Test
        void rampWaitsUntilItsTicksBeforeDetonation() {
            assertTrue(DomeRamp.rampAt(DomeRamp.RAMP_TICKS + 1, 0.5f, true, false).isEmpty());
            assertTrue(DomeRamp.rampAt(DomeRamp.RAMP_TICKS, 0f, true, false).isEmpty());
        }

        @Test
        void rampRunsInsideTheOrbsJitterWindow() {
            assertTrue(DomeRamp.RAMP_TICKS > 0 && DomeRamp.RAMP_TICKS <= FuseOrbVisual.JITTER_TICKS);
        }

        @Test
        void rampRunsFromItsStartToDetonation() {
            assertEquals(0.0, DomeRamp.rampAt(DomeRamp.RAMP_TICKS, TOLERANCE, true, false).orElseThrow(),
                    1e-4);
            assertEquals(1f / DomeRamp.RAMP_TICKS, DomeRamp.rampAt(DomeRamp.RAMP_TICKS - 1, 0f, true, false)
                    .orElseThrow(), TOLERANCE);
            assertEquals(1.0, DomeRamp.rampAt(1, 1f, true, false).orElseThrow(), TOLERANCE);
            assertEquals(1.0, DomeRamp.rampAt(0, 0f, true, false).orElseThrow(), TOLERANCE);
        }

        @Test
        void fuseHeldOnATriggerHoldsTheRampAtItsStart() {
            OptionalDouble held = DomeRamp.rampAt(-1, 0.7f, true, false);
            assertEquals(0.0, held.orElseThrow(), 0.0);
            assertEquals(0f, DomeRamp.radius((float) held.orElseThrow(), 2f), 0f);
            assertEquals(0, DomeRamp.alpha((float) held.orElseThrow()));
        }

        @Test
        void tunnelMarkerDrawsNoRamp() {
            ChainBurnouts burnouts = new ChainBurnouts(TUNNEL_ABILITY::equals);
            assertTrue(DomeRamp.rampAt(0, 0.5f, burnouts.playsBurnout(TUNNEL_ABILITY), false).isEmpty());
            assertTrue(DomeRamp.rampAt(0, 0.5f, burnouts.playsBurnout(DOME_ABILITY), false).isPresent());
        }

        @Test
        void runningProgramDrawsNoRamp() {
            assertTrue(DomeRamp.rampAt(0, 0.5f, true, true).isEmpty());
        }
    }

    @Nested
    class WhatTheRampDraws {

        @Test
        void radiusEasesInFromNothingToTheFirstFrame() {
            assertEquals(0f, DomeRamp.radius(0f, 2f), 0f);
            assertEquals(2f, DomeRamp.radius(1f, 2f), TOLERANCE);
            float firstHalf = DomeRamp.radius(0.5f, 2f);
            assertTrue(firstHalf < 2f - firstHalf, "the radius does not ease in");
        }

        @Test
        void alphaFadesInFromNothingToFull() {
            assertEquals(0, DomeRamp.alpha(0f));
            assertEquals(128, DomeRamp.alpha(0.5f));
            assertEquals(255, DomeRamp.alpha(1f));
        }

        @Test
        void firstDrawnProgressIsOneTickIn() {
            assertEquals(1f / 14, DomeRamp.firstDrawnProgress(14), TOLERANCE);
            assertEquals(1f, DomeRamp.firstDrawnProgress(0), 0f);
        }
    }
}
