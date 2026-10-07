package com.mercuriusxeno.goo.ability.program;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The leaf steps' pure decisions: the court step's roll lands under the
 * percent chance its JSON names (decision vitality-waves-regenerate-and-court).
 */
class LeafStepsTest {

    @Nested
    class CourtRoll {

        @Test
        void aRollUnderTheChanceCourts() {
            assertTrue(LeafSteps.courts(0.25f, 0.0024f));
        }

        @Test
        void aRollAtTheChanceDoesNotCourt() {
            assertFalse(LeafSteps.courts(25f, 0.25f));
        }

        @Test
        void aRollOverTheChanceDoesNotCourt() {
            assertFalse(LeafSteps.courts(0.25f, 0.0026f));
        }

        @Test
        void aZeroChanceNeverCourts() {
            assertFalse(LeafSteps.courts(0f, 0f));
        }

        @Test
        void aFullChanceAlwaysCourts() {
            assertTrue(LeafSteps.courts(100f, 0.9999f));
        }
    }
}
