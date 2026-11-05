package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.ability.frost.FrostCurve;
import com.mercuriusxeno.goo.ability.frost.Frozen;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers the shared seam answering whether a goo hold holds a mob, and the gate on a mob's own action. */
class HeldMobsTest {

    private static final FrostCurve CURVE = new FrostCurve(0, 0.01f, 0.5f);
    private static final Frozen FULL = new Frozen(Frozen.FULL, 0, CURVE);
    private static final Frozen HALF = new Frozen(0.5f, 0, CURVE);

    @Nested
    class Seam {

        @Test
        void fullyFrozenMobIsHeld() {
            assertTrue(HeldMobs.held(false, FULL, false, false, false));
        }

        @Test
        void stunnedMobIsHeld() {
            assertTrue(HeldMobs.held(false, null, true, false, false));
        }

        @Test
        void grabbedMobIsHeld() {
            assertTrue(HeldMobs.held(false, null, false, true, false));
        }

        @Test
        void rewindingMobIsHeld() {
            assertTrue(HeldMobs.held(false, null, false, false, true));
        }

        @Test
        void mobWithItsAiSetOffIsHeld() {
            assertTrue(HeldMobs.held(true, null, false, false, false));
        }

        @Test
        void halfFrozenMobIsNotHeld() {
            assertFalse(HeldMobs.held(false, HALF, false, false, false));
        }

        @Test
        void unmarkedMobIsNotHeld() {
            assertFalse(HeldMobs.held(false, null, false, false, false));
        }
    }

    @Nested
    class Gate {

        @Test
        void heldMobSkipsItsOwnAction() {
            assertFalse(HeldMobs.ownActionRuns(true, true));
        }

        @Test
        void unheldMobRunsWhatVanillaRuns() {
            assertTrue(HeldMobs.ownActionRuns(true, false));
            assertFalse(HeldMobs.ownActionRuns(false, false));
        }
    }
}
