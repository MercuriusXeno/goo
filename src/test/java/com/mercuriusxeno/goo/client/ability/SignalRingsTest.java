package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Signal's rings fly from the hand to the range one after another, evenly
 * staggered, each expanding as it flies and fading as it expands, each a
 * closed ring square to the aim (decision signal-wave-toggles-each-device-once).
 */
class SignalRingsTest {

    private static final double EPSILON = 1e-9;
    private static final Vec3 EAST = new Vec3(1, 0, 0);

    @Nested
    class Flight {

        @Test
        void ringsFlyEvenlyStaggered() {
            double first = SignalRings.flightShare(0.3, 0);
            double second = SignalRings.flightShare(0.3, 1);
            assertEquals(1.0 / SignalRings.RINGS, second - first, EPSILON);
        }

        @Test
        void aRingStartsOverAtTheHandOnceItReachesTheRange() {
            assertEquals(SignalRings.flightShare(0.1, 0),
                    SignalRings.flightShare(0.1 + SignalRings.FLIGHT_SECONDS, 0), EPSILON);
        }

        @Test
        void aRingIsUnseenAtEitherEnd() {
            assertEquals(0f, SignalRings.opacity(0));
            assertEquals(0f, SignalRings.opacity(1));
        }

        @Test
        void aRingFadesAsItExpands() {
            assertTrue(SignalRings.opacity(0.7) < SignalRings.opacity(0.3));
            assertTrue(SignalRings.radiusAt(0.7) > SignalRings.radiusAt(0.3));
        }

        @Test
        void aRingExpandsFromTheHandRadiusToTheEndRadius() {
            assertEquals(SignalRings.HAND_RADIUS, SignalRings.radiusAt(0), EPSILON);
            assertEquals(SignalRings.END_RADIUS, SignalRings.radiusAt(1), EPSILON);
        }
    }

    @Nested
    class Shape {

        @Test
        void aRingIsClosedAndSquareToTheAim() {
            Vec3 center = new Vec3(3, 1, 0);
            Vec3[] ring = SignalRings.ringPoints(center, EAST, 0.5, 12);
            assertEquals(ring[0].x, ring[ring.length - 1].x, EPSILON);
            assertEquals(ring[0].y, ring[ring.length - 1].y, EPSILON);
            assertEquals(ring[0].z, ring[ring.length - 1].z, EPSILON);
            for (Vec3 point : ring) {
                assertEquals(center.x, point.x, EPSILON);
                assertEquals(0.5, point.distanceTo(center), EPSILON);
            }
        }
    }
}
