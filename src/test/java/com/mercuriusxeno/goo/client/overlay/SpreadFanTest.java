package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.ability.Charge;
import com.mercuriusxeno.goo.ability.crystal.KnifeRain;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Knives' spread fan spans the cone the release slings at the current
 * charge, symmetric about the look, its edges tracing the knives' own
 * flight from the hand the knives leave (decision knives-aims-a-spread-fan-not-a-blob-line).
 */
class SpreadFanTest {

    private static final Charge KNIVES = new Charge(30, 24, 50, 8);
    private static final double TOLERANCE = 1e-9;
    private static final Vec3 EYE = new Vec3(2, 70, -5);
    private static final float YAW = 30f;
    private static final float PITCH = 10f;
    private static final Vec3 LOOK = Vec3.directionFromRotation(PITCH, YAW);
    private static final Vec3 HAND = KnifeRain.handPoint(EYE, YAW, LOOK, true);
    private static final SpreadFan.FanReach FLAT_GROUND = new SpreadFan.FanReach(EYE.y - 1.6, 30);

    private static SpreadFan.Fan fanAt(float share) {
        return SpreadFan.of(HAND, YAW, PITCH, KNIVES, share, FLAT_GROUND);
    }

    @Nested
    class Spread {

        @ParameterizedTest
        @ValueSource(floats = {0f, 1f})
        void theEdgesSitHalfTheChargedSpreadEachSideOfTheLook(float share) {
            SpreadFan.Fan fan = fanAt(share);
            double half = KNIVES.spreadDegrees(share) / 2;

            assertEquals(YAW - half, fan.leftYaw(), TOLERANCE);
            assertEquals(YAW + half, fan.rightYaw(), TOLERANCE);
        }

        @Test
        void aFullChargeFansWiderThanAFreshPress() {
            assertTrue(widthOf(fanAt(1f)) > widthOf(fanAt(0f)));
        }

        private static double widthOf(SpreadFan.Fan fan) {
            return fan.leftEdge().getLast().distanceTo(fan.rightEdge().getLast());
        }
    }

    @Nested
    class Flight {

        @Test
        void eachEdgeTracesAKnifesFlightAtItsYaw() {
            SpreadFan.Fan fan = fanAt(1f);
            double half = KNIVES.spreadDegrees(1f) / 2;
            Vec3 leftVelocity = KnifeRain.aimedVelocity(YAW, PITCH, -half);
            Vec3 rightVelocity = KnifeRain.aimedVelocity(YAW, PITCH, half);

            assertTrue(fan.leftEdge().size() > 1);
            for (int tick = 0; tick < fan.leftEdge().size(); tick++) {
                assertEquals(0, KnifeRain.positionAt(HAND, leftVelocity, tick).distanceTo(fan.leftEdge().get(tick)),
                        TOLERANCE);
                assertEquals(0, KnifeRain.positionAt(HAND, rightVelocity, tick).distanceTo(fan.rightEdge().get(tick)),
                        TOLERANCE);
            }
        }

        @Test
        void everyLineLeavesTheHandTheKnivesLeave() {
            SpreadFan.Fan fan = fanAt(0.5f);

            for (List<Vec3> line : List.of(fan.leftEdge(), fan.center(), fan.rightEdge())) {
                assertEquals(HAND, line.getFirst());
            }
        }

        @Test
        void theFanEndsWhereTheCenterKnifeFallsToTheFloor() {
            SpreadFan.Fan fan = fanAt(1f);
            Vec3 end = fan.center().getLast();
            Vec3 before = fan.center().get(fan.center().size() - 2);

            assertTrue(end.y <= FLAT_GROUND.floorY(), "the center ends at the floor, ended at " + end.y);
            assertTrue(before.y > FLAT_GROUND.floorY(), "the center runs until it reaches the floor");
        }

        @Test
        void aFanAimedPastAWallStopsAtTheWallsDistance() {
            SpreadFan.FanReach wall = new SpreadFan.FanReach(EYE.y - 50, 4);
            SpreadFan.Fan fan = SpreadFan.of(HAND, YAW, PITCH, KNIVES, 1f, wall);
            Vec3 end = fan.center().getLast().subtract(HAND);

            assertTrue(Math.hypot(end.x, end.z) >= 4, "the center reaches the wall");
            assertTrue(Math.hypot(end.x, end.z) < 5, "the center stops at the wall, reached " + end);
        }

        @Test
        void theRibsJoinTheEdgesAndTheLastClosesTheFan() {
            SpreadFan.Fan fan = fanAt(1f);
            List<Vec3> last = fan.ribs().getLast();

            assertEquals(SpreadFan.RIBS, fan.ribs().size());
            assertEquals(0, fan.leftEdge().getLast().distanceTo(last.getFirst()), TOLERANCE);
            assertEquals(0, fan.rightEdge().getLast().distanceTo(last.getLast()), TOLERANCE);
        }
    }
}
