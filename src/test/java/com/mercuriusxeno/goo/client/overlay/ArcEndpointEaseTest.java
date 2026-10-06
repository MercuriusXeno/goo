package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.client.TargetResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The aim arc slides its endpoint and its peak weight from the last target to
 * the new one over the ease time rather than snapping, and the ease time is
 * 0.05 seconds (decisions aim-line-lerps-toward-target,
 * aim-arc-slides-in-real-time).
 */
class ArcEndpointEaseTest {

    private static final double EASE = 0.1;
    private static final double EPSILON = 1e-9;
    private static final Vec3 FROM = new Vec3(0, 64, 0);
    private static final Vec3 TO = new Vec3(4, 66, -2);

    private static void assertSamePoint(Vec3 expected, Vec3 actual) {
        assertEquals(0, expected.distanceTo(actual), EPSILON, () -> expected + " vs " + actual);
    }

    @Nested
    class Endpoint {
        @Test
        void startsAtPreviousEndpoint() {
            assertSamePoint(FROM, ArcEndpointEase.easeEndpoint(FROM, TO, 0, EASE));
        }

        @Test
        void reachesNewEndpointAtEaseTime() {
            assertSamePoint(TO, ArcEndpointEase.easeEndpoint(FROM, TO, EASE, EASE));
        }

        @Test
        void holdsNewEndpointPastEaseTime() {
            assertSamePoint(TO, ArcEndpointEase.easeEndpoint(FROM, TO, EASE * 3, EASE));
        }

        @Test
        void movesAlongSegmentTowardNewEndpoint() {
            Vec3 quarter = ArcEndpointEase.easeEndpoint(FROM, TO, EASE / 4, EASE);
            Vec3 half = ArcEndpointEase.easeEndpoint(FROM, TO, EASE / 2, EASE);
            assertTrue(half.distanceTo(TO) < quarter.distanceTo(TO));
            assertTrue(quarter.distanceTo(TO) < FROM.distanceTo(TO));
            assertEquals(FROM.distanceTo(TO), FROM.distanceTo(half) + half.distanceTo(TO), EPSILON);
        }

        @Test
        void appearsAtOwnEndpointWithNoPrevious() {
            assertSamePoint(TO, ArcEndpointEase.easeEndpoint(null, TO, 0, EASE));
        }
    }

    @Nested
    class GrannyWeight {
        @Test
        void startsAtPreviousWeight() {
            assertEquals(0, ArcEndpointEase.easeGrannyWeight(0, 1, 0, EASE), EPSILON);
        }

        @Test
        void reachesNewWeightAtEaseTimeAndBeyond() {
            assertEquals(1, ArcEndpointEase.easeGrannyWeight(0, 1, EASE, EASE), EPSILON);
            assertEquals(1, ArcEndpointEase.easeGrannyWeight(0, 1, EASE * 3, EASE), EPSILON);
        }

        @Test
        void sitsBetweenWeightsAtHalfEaseTime() {
            double half = ArcEndpointEase.easeGrannyWeight(1, 0, EASE / 2, EASE);
            assertTrue(half > 0 && half < 1, () -> "half weight " + half);
        }
    }

    @Test
    void theSlideTakesFiftyMilliseconds() {
        assertEquals(0.05, ArcEndpointEase.EASE_SECONDS, EPSILON);
        assertEquals(0.5, ArcEndpointEase.easeProgress(0.025, ArcEndpointEase.EASE_SECONDS), EPSILON);
        assertEquals(1, ArcEndpointEase.easeProgress(0.05, ArcEndpointEase.EASE_SECONDS), EPSILON);
    }

    @Test
    void zeroEaseTimeSnapsToNewEndpointAndWeight() {
        assertSamePoint(TO, ArcEndpointEase.easeEndpoint(FROM, TO, 0, 0));
        assertEquals(1, ArcEndpointEase.easeGrannyWeight(0, 1, 0, 0), EPSILON);
    }

    /** A point moving with the cursor carries the ease on, so the line follows it (decision target-kind-configured-per-ability). */
    @Nested
    class Restarts {

        private static final BlockPos POS = new BlockPos(2, 64, 2);
        private static final TargetResult POINT = TargetResult.pointOnBlock(new Vec3(2.5, 64.5, 2.5), POS, Direction.UP);
        private static final TargetResult MOVED_POINT = TargetResult.pointOnBlock(new Vec3(2.6, 64.5, 2.5), POS, Direction.UP);
        private static final TargetResult BLOCK = TargetResult.block(POS, Direction.UP);

        @Test
        void aPointFollowingAPointCarriesTheEaseOn() {
            assertFalse(ArcEndpointEase.restartsEase(POINT, MOVED_POINT));
        }

        @Test
        void aChangeOfKindOrBlockRestartsTheEase() {
            assertTrue(ArcEndpointEase.restartsEase(BLOCK, POINT));
            assertTrue(ArcEndpointEase.restartsEase(POINT, BLOCK));
            assertTrue(ArcEndpointEase.restartsEase(BLOCK, TargetResult.block(POS.above(), Direction.UP)));
            assertTrue(ArcEndpointEase.restartsEase(null, POINT));
        }

        @Test
        void theCursorMovingOverTheSameBlockCarriesTheEaseOn() {
            assertFalse(ArcEndpointEase.restartsEase(BLOCK,
                    TargetResult.block(POS, Direction.UP, new Vec3(2.1, 65, 2.9))));
        }
    }

    /** The aim line ends at the exact point under the cursor (decision aim-point-follows-the-cursor). */
    @Nested
    class LineEnd {

        private static final BlockPos POS = new BlockPos(2, 64, 2);

        @Test
        void aFaceHitEndsWhereTheRayMetTheFaceNotItsCenter() {
            Vec3 met = new Vec3(2.15, 64.8, 2.0);
            TargetResult target = TargetResult.block(POS, Direction.NORTH, met);

            assertSamePoint(met, ArcEndpointEase.lineEnd(target));
            assertTrue(met.distanceTo(target.resolveEndpoint()) > EPSILON);
        }

        @Test
        void anEntityHitEndsWhereTheRayMetTheEntity() {
            Vec3 met = new Vec3(7.0, 65.3, 1.2);

            assertSamePoint(met, ArcEndpointEase.lineEnd(new TargetResult.EntityTarget(null, met)));
        }

        @Test
        void aFreeMissEndsAtTheRaysEnd() {
            Vec3 reach = new Vec3(40, 90, -30);

            assertSamePoint(reach, ArcEndpointEase.lineEnd(TargetResult.pointInAir(reach)));
        }

        @Test
        void nothingAimedEndsNoLine() {
            assertNull(ArcEndpointEase.lineEnd(TargetResult.NONE));
        }
    }
}
