package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.throwing.StreamCone;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Signal's rings fly from the hand to the range one after another, evenly
 * staggered, each expanding as it flies and fading as it expands, each a
 * closed ring square to the aim (decision signal-wave-toggles-each-device-once).
 */
class SignalRingsTest {

    private static final double EPSILON = 1e-9;
    private static final Vec3 EAST = new Vec3(1, 0, 0);
    /** pulse_signal.json's range and cone. */
    private static final double RANGE = 8;
    private static final double CONE = 40;

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
            assertTrue(SignalRings.radiusAt(0.7, RANGE, CONE) > SignalRings.radiusAt(0.3, RANGE, CONE));
        }

        @Test
        void aRingLeavesTheHandSmall() {
            assertEquals(SignalRings.HAND_RADIUS, SignalRings.radiusAt(0, RANGE, CONE), EPSILON);
        }

        /** The rings trace the cone the wave toggles devices in: a ring's rim stands on the cone's rim. */
        @Test
        void aRingsRimStandsOnTheStreamConesRim() {
            double distance = RANGE * 0.6;
            double rim = SignalRings.radiusAt(0.6, RANGE, CONE) - SignalRings.HAND_RADIUS;
            Vec3 onTheRim = new Vec3(distance, rim, 0);
            assertTrue(StreamCone.contains(Vec3.ZERO, EAST, RANGE, CONE, onTheRim.scale(0.999)));
            assertFalse(StreamCone.contains(Vec3.ZERO, EAST, RANGE, CONE, new Vec3(distance, rim * 1.01, 0)));
        }
    }

    @Nested
    class Shape {

        @Test
        void aRingIsClosedAndSquareToTheAim() {
            Vec3 center = new Vec3(3, 1, 0);
            Vec3[] ring = SignalRings.ringPoints(center, EAST, 0.5, 12, 0);
            assertEquals(ring[0].x, ring[ring.length - 1].x, EPSILON);
            assertEquals(ring[0].y, ring[ring.length - 1].y, EPSILON);
            assertEquals(ring[0].z, ring[ring.length - 1].z, EPSILON);
            for (Vec3 point : ring) {
                assertEquals(center.x, point.x, EPSILON);
                assertEquals(0.5, point.distanceTo(center), EPSILON);
            }
        }

        /** Pulser's rings are squares whose sides stand at the cone's radius, upright to the aim. */
        @Test
        void aSquareRingsSidesStandAtTheRadiusUpright() {
            Vec3 center = new Vec3(3, 1, 0);
            Vec3[] square = SignalRings.RingShape.SQUARE.points(center, EAST, 0.5);
            assertEquals(5, square.length);
            for (int corner = 0; corner < 4; corner++) {
                Vec3 midSide = square[corner].add(square[corner + 1]).scale(0.5);
                assertEquals(0.5, midSide.distanceTo(center), EPSILON);
                Vec3 side = square[corner + 1].subtract(square[corner]);
                assertTrue(Math.abs(side.y) < EPSILON || Math.abs(side.z) < EPSILON, "upright side " + side);
            }
        }
    }
}
