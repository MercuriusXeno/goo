package com.mercuriusxeno.goo.block.crucible;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the drift that draws items off the crucible's rim walls, down
 * its mouth and to the basin center, and the rest that gates consuming them
 * (decisions rim-and-mouth-items-slide-inward, consume-at-rest-in-place).
 */
class CrucibleItemDriftTest {

    private static final double EPSILON = 1e-9;
    private static final double CENTER = 0.5;
    private static final double WALL_MIDDLE = 3.0 / 16.0;
    private static final double LEDGE_MIDDLE = 1.0 / 16.0;

    /** Wall tops and the mouth column answer a push toward the center. */
    @Nested
    class TowardCenter {

        @Test
        void westWallTopPushesEast() {
            Vec3 nudge = CrucibleItemDrift.inwardNudge(WALL_MIDDLE, CrucibleBasin.RIM_Y, CENTER);
            assertTrue(nudge.x > 0);
            assertEquals(0.0, nudge.z, EPSILON);
            assertEquals(0.0, nudge.y, EPSILON);
        }

        @Test
        void eastWallTopPushesWest() {
            assertTrue(CrucibleItemDrift.inwardNudge(1.0 - WALL_MIDDLE, CrucibleBasin.RIM_Y, CENTER).x < 0);
        }

        @Test
        void northWallTopPushesSouth() {
            assertTrue(CrucibleItemDrift.inwardNudge(CENTER, CrucibleBasin.RIM_Y, WALL_MIDDLE).z > 0);
        }

        @Test
        void southWallTopPushesNorth() {
            assertTrue(CrucibleItemDrift.inwardNudge(CENTER, CrucibleBasin.RIM_Y, 1.0 - WALL_MIDDLE).z < 0);
        }

        @Test
        void itemOverhangingTheWallsOuterFacePushesInward() {
            assertTrue(CrucibleItemDrift.inwardNudge(0.01, CrucibleBasin.RIM_Y, CENTER).x > 0);
        }

        @Test
        void itemFallingOverTheMouthIsDrawnToCenter() {
            Vec3 nudge = CrucibleItemDrift.inwardNudge(0.3, CrucibleBasin.RIM_Y + 0.3, 0.7);
            assertEquals((CENTER - 0.3) * CrucibleItemDrift.NUDGE_GAIN, nudge.x, EPSILON);
            assertEquals((CENTER - 0.7) * CrucibleItemDrift.NUDGE_GAIN, nudge.z, EPSILON);
        }

        @Test
        void nudgeAddsToAThrownItemsMotion() {
            Vec3 thrown = new Vec3(0.2, -0.3, -0.1);
            Vec3 nudge = CrucibleItemDrift.inwardNudge(0.3, CrucibleBasin.RIM_Y + 0.3, CENTER);
            Vec3 after = CrucibleItemDrift.nudgedDelta(thrown, nudge);
            assertEquals(thrown.x + nudge.x, after.x, EPSILON);
            assertEquals(thrown.y, after.y, EPSILON);
            assertEquals(thrown.z, after.z, EPSILON);
        }
    }

    /** The ledge, the cavity and the air beside the block answer nothing. */
    @Nested
    class Still {

        @Test
        void outerLedgeIsLeftAlone() {
            assertEquals(Vec3.ZERO, CrucibleItemDrift.inwardNudge(LEDGE_MIDDLE, CrucibleShape.LEDGE_Y, CENTER));
        }

        @Test
        void itemInsideTheCavityIsLeftToSettle() {
            assertEquals(Vec3.ZERO, CrucibleItemDrift.inwardNudge(0.3, CrucibleBasin.FLOOR_Y, 0.6));
            assertEquals(Vec3.ZERO, CrucibleItemDrift.inwardNudge(0.3, CrucibleBasin.RIM_Y - 0.1, 0.6));
        }

        @Test
        void itemHighOverTheMouthIsLeftToFall() {
            assertEquals(Vec3.ZERO, CrucibleItemDrift.inwardNudge(0.3,
                CrucibleBasin.RIM_Y + CrucibleItemDrift.MOUTH_COLUMN_HEIGHT + 0.1, CENTER));
        }

        @Test
        void itemBesideTheBlockIsLeftAlone() {
            assertEquals(Vec3.ZERO, CrucibleItemDrift.inwardNudge(-0.2, CrucibleBasin.RIM_Y, CENTER));
        }
    }

    /** Inside the cavity an item is drawn to the center and slowed (decision consume-at-rest-in-place). */
    @Nested
    class Settle {

        @Test
        void offCenterItemIsDrawnTowardTheCenter() {
            Vec3 settled = CrucibleItemDrift.settledDelta(Vec3.ZERO, 0.6, 0.4);
            assertTrue(settled.x < 0);
            assertTrue(settled.z > 0);
        }

        @Test
        void motionIsDampedAtTheCenter() {
            Vec3 settled = CrucibleItemDrift.settledDelta(new Vec3(0.1, -0.2, -0.1), CENTER, CENTER);
            assertEquals(0.1 * (1 - CrucibleItemDrift.SETTLE_DAMPING), settled.x, EPSILON);
            assertEquals(-0.1 * (1 - CrucibleItemDrift.SETTLE_DAMPING), settled.z, EPSILON);
            assertEquals(-0.2, settled.y, EPSILON);
        }
    }

    /** The rest predicate that alone lets an item be consumed. */
    @Nested
    class RestsAtCenter {

        private static final double SURFACE = 0.6;

        @Test
        void stoppedItemAtTheCenterOnTheSurfaceRests() {
            assertTrue(CrucibleItemDrift.restsAtCenter(CENTER, CENTER, 0.0, SURFACE, SURFACE));
        }

        @Test
        void stoppedItemOnTheEmptyFloorRests() {
            assertTrue(CrucibleItemDrift.restsAtCenter(CENTER + 0.01, CENTER, 0.001,
                CrucibleBasin.FLOOR_Y, CrucibleBasin.FLOOR_Y));
        }

        @Test
        void movingItemAtTheCenterDoesNotRest() {
            assertFalse(CrucibleItemDrift.restsAtCenter(CENTER, CENTER, 0.01, SURFACE, SURFACE));
        }

        @Test
        void stoppedItemOffCenterDoesNotRest() {
            assertFalse(CrucibleItemDrift.restsAtCenter(CENTER + 0.05, CENTER, 0.0, SURFACE, SURFACE));
        }

        @Test
        void itemFallingAboveTheSurfaceDoesNotRest() {
            assertFalse(CrucibleItemDrift.restsAtCenter(CENTER, CENTER, 0.0, SURFACE + 0.1, SURFACE));
        }
    }
}
