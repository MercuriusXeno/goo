package com.mercuriusxeno.goo.block.crucible;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the pull field over the crucible and the lift up its sides
 * (decision rim-and-mouth-items-slide-inward).
 */
class CrucibleItemDriftTest {

    private static final double EPSILON = 1e-9;
    private static final double CENTER = 0.5;
    private static final double WALL_MIDDLE = 3.0 / 16.0;
    private static final double LEDGE_MIDDLE = 1.0 / 16.0;
    private static final double IN_FIELD = CrucibleBasin.RIM_Y + 0.1;
    private static final Vec3 THROWN = new Vec3(0.4, -0.2, -0.3);

    /** The field over the block damps a throw and draws it to the center. */
    @Nested
    class PullField {

        @Test
        void thrownItemIsSlowedAndDrawnToTheCenter() {
            Vec3 after = CrucibleItemDrift.fieldDelta(THROWN, 0.8, IN_FIELD, 0.3);
            double keep = 1.0 - CrucibleItemDrift.FIELD_DAMPING;
            assertEquals(THROWN.x * keep + (CENTER - 0.8) * CrucibleItemDrift.FIELD_GAIN, after.x, EPSILON);
            assertEquals(THROWN.z * keep + (CENTER - 0.3) * CrucibleItemDrift.FIELD_GAIN, after.z, EPSILON);
        }

        @Test
        void fieldKeepsTheFall() {
            assertEquals(THROWN.y, CrucibleItemDrift.fieldDelta(THROWN, 0.8, IN_FIELD, 0.3).y, EPSILON);
        }

        @Test
        void itemOnAWallTopIsDrawnInward() {
            Vec3 after = CrucibleItemDrift.fieldDelta(Vec3.ZERO, WALL_MIDDLE, CrucibleBasin.RIM_Y, CENTER);
            assertTrue(after.x > 0);
        }

        @Test
        void fieldReachesAQuarterBlockOverTheRim() {
            double top = CrucibleBasin.RIM_Y + CrucibleItemDrift.FIELD_HEIGHT;
            assertTrue(CrucibleItemDrift.fieldDelta(THROWN, 0.8, top, CENTER).x < THROWN.x);
            assertSame(THROWN, CrucibleItemDrift.fieldDelta(THROWN, 0.8, top + 0.01, CENTER));
        }

        @Test
        void itemWhoseEdgeIsInReachIsPulled() {
            double edgeInReach = -(CrucibleItemDrift.FIELD_REACH + CrucibleItemDrift.ITEM_HALF_WIDTH) + 0.01;
            assertTrue(CrucibleItemDrift.fieldDelta(THROWN, edgeInReach, IN_FIELD, CENTER).x < THROWN.x);
            assertTrue(CrucibleItemDrift.fieldDelta(THROWN, CENTER, IN_FIELD, 1.0 - edgeInReach).z > THROWN.z);
        }

        @Test
        void itemWhoseEdgeIsPastTheReachIsLeftAlone() {
            double edgeOut = -(CrucibleItemDrift.FIELD_REACH + CrucibleItemDrift.ITEM_HALF_WIDTH) - 0.01;
            assertSame(THROWN, CrucibleItemDrift.fieldDelta(THROWN, edgeOut, IN_FIELD, CENTER));
            assertSame(THROWN, CrucibleItemDrift.fieldDelta(THROWN, CENTER, IN_FIELD, 1.0 - edgeOut));
        }
    }

    /** An item below the rim, on the ledge or against the sides, is lifted over the collar. */
    @Nested
    class Lift {

        @Test
        void ledgeItemIsLiftedInward() {
            Vec3 after = CrucibleItemDrift.fieldDelta(Vec3.ZERO, LEDGE_MIDDLE, CrucibleShape.LEDGE_Y, CENTER);
            assertEquals(CrucibleItemDrift.LIFT_SPEED, after.y, EPSILON);
            assertEquals((CENTER - LEDGE_MIDDLE) * CrucibleItemDrift.LIFT_INWARD_GAIN, after.x, EPSILON);
        }

        @Test
        void itemTouchingTheSideIsLiftedInward() {
            Vec3 after = CrucibleItemDrift.fieldDelta(THROWN, 1.0 + CrucibleItemDrift.ITEM_HALF_WIDTH, 0.7, CENTER);
            assertEquals(CrucibleItemDrift.LIFT_SPEED, after.y, EPSILON);
            assertTrue(after.x < 0);
        }

        @Test
        void risingItemKeepsItsFasterClimb() {
            Vec3 rising = new Vec3(0.0, 0.35, 0.0);
            assertEquals(0.35, CrucibleItemDrift.fieldDelta(rising, LEDGE_MIDDLE, 0.9, CENTER).y, EPSILON);
        }

        @Test
        void liftReachesAnEighthUnderTheOuterTopEdge() {
            double floor = CrucibleShape.LEDGE_Y - 0.125;
            assertEquals(floor, CrucibleItemDrift.LIFT_FLOOR_Y, EPSILON);
            Vec3 after = CrucibleItemDrift.fieldDelta(THROWN, -CrucibleItemDrift.ITEM_HALF_WIDTH, floor, CENTER);
            assertEquals(CrucibleItemDrift.LIFT_SPEED, after.y, EPSILON);
        }

        @Test
        void itemFurtherDownTheSideIsLeftAlone() {
            assertSame(THROWN, CrucibleItemDrift.fieldDelta(THROWN, -0.1, CrucibleItemDrift.LIFT_FLOOR_Y - 0.01, CENTER));
        }

        @Test
        void itemInsideTheCavityIsNotLifted() {
            assertSame(THROWN, CrucibleItemDrift.fieldDelta(THROWN, 0.4, CrucibleShape.LEDGE_Y, 0.6));
        }
    }
}
