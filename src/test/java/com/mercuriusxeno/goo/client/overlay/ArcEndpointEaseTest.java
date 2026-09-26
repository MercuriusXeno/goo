package com.mercuriusxeno.goo.client.overlay;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The aim arc slides its endpoint and its peak weight from the last target to
 * the new one over the ease time rather than snapping (decision
 * aim-line-lerps-toward-target).
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
    void zeroEaseTimeSnapsToNewEndpoint() {
        assertSamePoint(TO, ArcEndpointEase.easeEndpoint(FROM, TO, 0, 0));
    }
}
