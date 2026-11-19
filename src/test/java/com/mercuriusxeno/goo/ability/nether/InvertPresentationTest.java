package com.mercuriusxeno.goo.ability.nether;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Invert's presentation seam: an inverted player's first person camera rolls half a turn and its model draws upside
 * down, its third person camera stays unrolled over the flipped model, and without Invert both read upright.
 * invert-flips-your-gravity-and-lets-you-walk-on-ceilings
 */
class InvertPresentationTest {

    private static final float HALF_TURN = 180F;

    @Nested
    class WithInvert {

        private final InvertPresentation firstPerson = InvertPresentation.of(true, true);

        @Test
        void rollsTheCameraHalfATurn() {
            assertEquals(HALF_TURN, firstPerson.cameraRoll());
        }

        @Test
        void drawsTheModelUpsideDown() {
            assertTrue(firstPerson.modelUpsideDown());
        }

        @Test
        void steersTowardTheSidesItSees() {
            assertEquals(-1F, firstPerson.steerSign());
        }

        @Test
        void inThirdPersonShowsTheFlippedModelUnrolled() {
            InvertPresentation thirdPerson = InvertPresentation.of(true, false);
            assertEquals(0F, thirdPerson.cameraRoll());
            assertTrue(thirdPerson.modelUpsideDown());
            assertEquals(1F, thirdPerson.steerSign());
        }
    }

    @Nested
    class WithoutInvert {

        private final InvertPresentation firstPerson = InvertPresentation.of(false, true);

        @Test
        void leavesTheCameraUnrolled() {
            assertEquals(0F, firstPerson.cameraRoll());
        }

        @Test
        void drawsTheModelUpright() {
            assertFalse(firstPerson.modelUpsideDown());
        }

        @Test
        void steersAsVanilla() {
            assertEquals(1F, firstPerson.steerSign());
        }
    }
}
