package com.mercuriusxeno.goo.ability.program;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The held sweep's reveal is a thin shell at the sphere's rim: its
 * thickness a named share of the radius, the cells inside it undrawn; and
 * the hold keeps it pulsing, one pulse after another.
 * decision held-sweeps-share-one-fast-shell-visual
 */
class HeldSweepPaceTest {

    private static final HeldSweepPace SWEEP = new HeldSweepPace(64);
    private static final double[] RADII = {2, 8, 16, 32, 64};
    private static final double JUST = 0.01;

    @Test
    void pulsesRunOneAfterAnotherWhileHeld() {
        int pulse = SWEEP.pulseTicks();

        assertEquals(1, SWEEP.pulseTick(1));
        assertEquals(pulse, SWEEP.pulseTick(pulse));
        assertEquals(1, SWEEP.pulseTick(pulse + 1));
    }

    @Test
    void aPulseReachesItsRangeAndFadesOverItsRest() {
        assertEquals(0, SWEEP.radiusAt(0), 0);
        assertEquals(SWEEP.range(), SWEEP.radiusAt(HeldSweepPace.EXPAND_TICKS), 0);
        assertEquals(1f, SWEEP.strengthAt(HeldSweepPace.EXPAND_TICKS), 0f);
        assertEquals(0f, SWEEP.strengthAt(SWEEP.pulseTicks()), 0f);
    }

    @Test
    void theShellGlowsWholeAtTheRimAndNothingAtItsInnerEdge() {
        double radius = 32;
        assertEquals(1f, SWEEP.glowAt(radius, radius), 1e-6f);
        assertEquals(0f, SWEEP.glowAt(radius - SWEEP.shellThickness(radius), radius), 0f);
    }

    @Test
    void theShellIsNoThickerThanItsShareOfTheRadius() {
        for (double radius : RADII) {
            double thickness = SWEEP.shellThickness(radius);
            assertTrue(thickness > 0 && thickness <= HeldSweepPace.SHELL_FRACTION * radius,
                    "at " + radius + " the shell stood " + thickness + " thick");
        }
    }

    @Test
    void aCellInsideTheRimDrawsNothing() {
        for (double radius : RADII) {
            double inner = radius - SWEEP.shellThickness(radius);
            assertFalse(SWEEP.drawsAt(inner - JUST, radius), "inside the rim at " + radius);
            assertFalse(SWEEP.drawsAt(0, radius), "the center at " + radius);
        }
    }

    @Test
    void aCellWithinTheShellDraws() {
        for (double radius : RADII) {
            assertTrue(SWEEP.drawsAt(radius - SWEEP.shellThickness(radius) / 2, radius), "within the shell at " + radius);
            assertTrue(SWEEP.drawsAt(radius, radius), "on the rim at " + radius);
            assertFalse(SWEEP.drawsAt(radius + JUST, radius), "past the rim at " + radius);
        }
    }
}
