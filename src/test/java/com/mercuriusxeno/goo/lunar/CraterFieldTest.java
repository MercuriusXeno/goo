package com.mercuriusxeno.goo.lunar;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A lunar crater sinks a bowl under the ground and rings it with a raised rim, and a cell rolled empty leaves its
 * ground untouched.
 * decision lunar-generates-from-its-own-noise-and-surface-rules
 */
class CraterFieldTest {

    private static final double TOLERANCE = 1e-9;
    private static final double RADIUS = 10.0;

    @Nested
    class Profile {

        @Test
        void theBowlFloorSinksAThirdOfTheRadius() {
            assertEquals(-RADIUS * CraterField.DEPTH_PER_RADIUS, CraterField.profile(0.0, RADIUS), TOLERANCE);
        }

        @Test
        void theRimCrestRisesAnEighthOfTheRadiusAtOneRadius() {
            assertEquals(RADIUS * CraterField.RIM_PER_RADIUS, CraterField.profile(1.0, RADIUS), TOLERANCE);
        }

        @Test
        void theRimFallsBackToTheGroundPastItsReach() {
            assertEquals(0.0, CraterField.profile(1.0 + CraterField.RIM_REACH, RADIUS), TOLERANCE);
        }
    }

    @Nested
    class Field {

        /** Cell (0, 0) holds a crater centred in it at half the size spread; every other cell holds none. */
        private static double rollOneCenteredCrater(int cellX, int cellZ, int roll) {
            if (cellX != 0 || cellZ != 0) {
                return 0.99;
            }
            return roll == 0 ? 0.0 : 0.5;
        }

        private static final int CENTER = CraterField.CELL / 2;
        private static final double CENTERED_RADIUS = CraterField.MIN_RADIUS + 0.25 * CraterField.RADIUS_SPREAD;

        @Test
        void aColumnAtACraterCenterReadsTheBowlFloor() {
            assertEquals(CraterField.profile(0.0, CENTERED_RADIUS),
                    CraterField.heightAt(CENTER, CENTER, Field::rollOneCenteredCrater), TOLERANCE);
        }

        @Test
        void aColumnOnACraterRimReadsAboveTheGround() {
            int rim = CENTER + (int) Math.round(CENTERED_RADIUS);
            assertTrue(CraterField.heightAt(rim, CENTER, Field::rollOneCenteredCrater) > 0.0);
        }

        @Test
        void aColumnNoCraterReachesReadsTheGround() {
            assertEquals(0.0, CraterField.heightAt(-CraterField.CELL, -CraterField.CELL,
                    Field::rollOneCenteredCrater), TOLERANCE);
        }
    }
}
