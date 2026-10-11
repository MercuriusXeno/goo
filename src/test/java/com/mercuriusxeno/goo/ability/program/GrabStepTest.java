package com.mercuriusxeno.goo.ability.program;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests Grab's hold distance by pitch and which left clicks throw through it
 * (decision grab-holds-and-throws-a-physics-body).
 */
class GrabStepTest {

    private static final double NEAR = 2;
    private static final double FAR = 4;
    private static final double EPSILON = 1e-6;
    private static final GrabStep GRAB = new GrabStep(8, NEAR, FAR, 1.5);

    @Nested
    class HoldDistance {

        @Test
        void levelOrUpwardLookHoldsAtFar() {
            assertEquals(FAR, GrabStep.holdDistance(0f, NEAR, FAR), EPSILON);
            assertEquals(FAR, GrabStep.holdDistance(-45f, NEAR, FAR), EPSILON);
        }

        @Test
        void downwardLookDrawsInLinearlyToNear() {
            assertEquals(3, GrabStep.holdDistance(GrabStep.FULL_DRAW_PITCH / 2, NEAR, FAR), EPSILON);
            assertEquals(NEAR, GrabStep.holdDistance(GrabStep.FULL_DRAW_PITCH, NEAR, FAR), EPSILON);
            assertEquals(NEAR, GrabStep.holdDistance(90f, NEAR, FAR), EPSILON);
        }
    }

    @Nested
    class ThrowsOnAttack {

        @Test
        void armedPressOnAGrabThrows() {
            assertTrue(GrabStep.throwsOnAttack(true, List.of(GRAB)));
        }

        @Test
        void unarmedPressOrAnotherAbilityPunches() {
            assertFalse(GrabStep.throwsOnAttack(false, List.of(GRAB)));
            assertFalse(GrabStep.throwsOnAttack(true, List.of(new HasteStep())));
        }
    }
}
