package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.ability.frost.FrostCurve;
import com.mercuriusxeno.goo.ability.frost.Frozen;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the shared seam answering whether a goo hold holds a mob, the gate on a mob's own action, and the
 * travel a held mob still falls by.
 */
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
    class Fall {

        private final Vec3 walking = new Vec3(0, 0, 0.98);

        @Test
        void mobAHoldHoldsTravelsWithItsAiOff() {
            assertTrue(HeldMobs.travelRuns(false, HeldMobs.heldByAHold(FULL, false, false, false)));
        }

        @Test
        void mobWithItsAiSetOffAloneFloatsAsVanillaHasIt() {
            assertFalse(HeldMobs.travelRuns(false, HeldMobs.heldByAHold(null, false, false, false)));
        }

        @Test
        void unheldMobTravelsAsVanillaHasIt() {
            assertTrue(HeldMobs.travelRuns(true, false));
            assertEquals(walking, HeldMobs.travelInput(walking, false));
        }

        @Test
        void heldMobTravelsOnNoInput() {
            assertEquals(Vec3.ZERO, HeldMobs.travelInput(walking, true));
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
