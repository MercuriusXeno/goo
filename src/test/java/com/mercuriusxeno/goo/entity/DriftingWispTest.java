package com.mercuriusxeno.goo.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A shed wisp's beam, stepped tick by tick from the center of the cell that
 * shed it, a cell under open air, to the center of its spot: it arrives on
 * one count of ticks whatever the distance, and never leaves the straight
 * line between the two.
 */
class DriftingWispTest {

    /** The shedding cell, open air over it. */
    private static final Vec3 CELL = Vec3.atCenterOf(BlockPos.ZERO);
    /** A spot one block off the cell. */
    private static final Vec3 NEAR_SPOT = Vec3.atCenterOf(new BlockPos(1, 0, 0));
    /** A spot sixteen blocks off the cell, as far as a full cell reaches. */
    private static final Vec3 FAR_SPOT = Vec3.atCenterOf(new BlockPos(10, 3, 3));
    private static final double ON_THE_LINE = 1e-9;

    @Nested
    class ArrivalCount {

        @Test
        void aNearSpotIsReachedOnTheArrivalCount() {
            assertArrivesOnTheCount(NEAR_SPOT);
        }

        @Test
        void aFarSpotIsReachedOnTheArrivalCount() {
            assertArrivesOnTheCount(FAR_SPOT);
        }

        private void assertArrivesOnTheCount(Vec3 spot) {
            int count = DriftingWisp.ARRIVAL_TICKS;
            assertNotEquals(spot, DriftingWisp.beamPosition(CELL, spot, count - 1),
                    "the wisp stood on " + spot + " a tick before the arrival count");
            assertEquals(spot, DriftingWisp.beamPosition(CELL, spot, count),
                    "the wisp stood off " + spot + " on the arrival count");
        }
    }

    @Nested
    class StraightLine {

        @Test
        void everyPositionLiesBetweenTheCellAndTheSpot() {
            for (int tick = 0; tick <= DriftingWisp.ARRIVAL_TICKS; tick++) {
                Vec3 at = DriftingWisp.beamPosition(CELL, FAR_SPOT, tick);
                double off = distanceOffTheSegment(at, CELL, FAR_SPOT);
                assertTrue(off < ON_THE_LINE, "tick " + tick + " stood " + off + " off the line, at " + at);
            }
        }

        private double distanceOffTheSegment(Vec3 at, Vec3 from, Vec3 to) {
            Vec3 way = to.subtract(from);
            double along = Math.clamp(at.subtract(from).dot(way) / way.lengthSqr(), 0, 1);
            return from.add(way.scale(along)).distanceTo(at);
        }
    }
}
