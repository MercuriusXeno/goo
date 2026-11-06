package com.mercuriusxeno.goo.ability.program;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests Grab's hold distance by pitch, its reach with and without
 * Telekinesis, and which left clicks throw through it
 * (decisions grab-holds-and-throws-a-physics-body, grab-reaches-telekinesis-range).
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
    class Reach {

        private static final double TELEKINESIS_REACH = 8;

        @Test
        void withoutTelekinesisReachesItsOwnRange() {
            assertEquals(8, GRAB.reachWith(null), EPSILON);
        }

        @Test
        void telekinesisAddsItsReachToTheRange() {
            AttributeModifier laid = new AttributeModifier(Identifier.fromNamespaceAndPath("goo", "telekinesis_entity_reach"),
                    TELEKINESIS_REACH, AttributeModifier.Operation.ADD_VALUE);
            assertEquals(16, GRAB.reachWith(laid), EPSILON);
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
            assertFalse(GrabStep.throwsOnAttack(true, List.of(new AncientStep())));
        }
    }
}
