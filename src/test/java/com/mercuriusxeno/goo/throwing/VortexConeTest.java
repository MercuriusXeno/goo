package com.mercuriusxeno.goo.throwing;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drain's miasma vortex lies along the player's whole look from the glove
 * hand and spans the cone the stream strikes, its length and widening read
 * from hex_drain's delivery (decision drain-casts-a-filled-tornadic-miasma-vortex-along-the-look).
 */
class VortexConeTest {

    private static final Vec3 HAND = new Vec3(10.5, 64.3, -3.25);
    private static final double TOLERANCE = 1e-9;
    private static final String DRAIN_JSON = "/data/goo/goo_abilities/hex_drain.json";

    private static double range;
    private static double coneDegrees;

    @BeforeAll
    static void readDrainsDelivery() throws IOException {
        try (InputStream in = VortexConeTest.class.getResourceAsStream(DRAIN_JSON)) {
            assertNotNull(in, DRAIN_JSON);
            JsonObject delivery = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("delivery");
            range = delivery.get("range").getAsDouble();
            coneDegrees = delivery.get("cone").getAsDouble();
        }
    }

    @Nested
    class Axis {

        private static final float LOOK_DOWN_PITCH = 60;
        private static final float YAW = 30;
        /** Vec3.directionFromRotation reads Mojang's float sine table, true to about this much. */
        private static final double FLOAT_TABLE_TOLERANCE = 1e-3;

        @Test
        void aLookPitchedDownTiltsTheVortexWithIt() {
            Vec3 look = Vec3.directionFromRotation(LOOK_DOWN_PITCH, YAW).normalize();
            VortexCone vortex = VortexCone.along(HAND, look, range, coneDegrees);
            assertAll(
                    () -> assertEquals(HAND, vortex.apex()),
                    () -> assertEquals(look.x, vortex.axis().x, TOLERANCE),
                    () -> assertEquals(look.z, vortex.axis().z, TOLERANCE),
                    () -> assertEquals(look.y, vortex.axis().y, TOLERANCE),
                    () -> assertEquals(-Math.sin(Math.toRadians(LOOK_DOWN_PITCH)), vortex.axis().y,
                            FLOAT_TABLE_TOLERANCE));
        }
    }

    @Nested
    class Span {

        private static final Vec3 LOOK = new Vec3(0.6, -0.48, 0.64).normalize();
        private static final int SAMPLES = 24;
        /** Keeps a sample off the strike's own rim and range, where rounding decides membership. */
        private static final double JUST_INSIDE = 0.999;
        private static final double PAST_THE_RANGE = 1.05;
        private static final double PAST_THE_RIM = 1.1;

        private VortexCone vortex() {
            return VortexCone.along(HAND, LOOK, range, coneDegrees);
        }

        @Test
        void itsLengthIsTheDeliverysRange() {
            assertEquals(range, vortex().length(), TOLERANCE);
        }

        @Test
        void itsRadiusIsTheDistanceTimesTheTangentOfHalfTheCone() {
            double tan = Math.tan(Math.toRadians(coneDegrees / 2));
            for (int sample = 0; sample <= SAMPLES; sample++) {
                double distance = range * sample / SAMPLES;
                assertEquals(distance * tan, vortex().radiusAt(distance), TOLERANCE, "at " + distance);
            }
        }

        @Test
        void everyPointTheStreamStrikesLiesInsideIt() {
            for (int tilt = 0; tilt <= SAMPLES; tilt++) {
                for (int turn = 0; turn < SAMPLES; turn++) {
                    for (int reach = 1; reach <= SAMPLES; reach++) {
                        Vec3 way = StreamCone.launchDirection(LOOK, coneDegrees, JUST_INSIDE * tilt / SAMPLES,
                                (double) turn / SAMPLES);
                        Vec3 point = HAND.add(way.scale(JUST_INSIDE * range * reach / SAMPLES));
                        assertTrue(StreamCone.contains(HAND, LOOK, range, coneDegrees, point), point::toString);
                        assertTrue(vortex().bounds(point), point::toString);
                        assertTrue(vortex().box(0).contains(point), point::toString);
                    }
                }
            }
        }

        @Test
        void aPointPastTheRangeLiesOutside() {
            assertFalse(vortex().bounds(HAND.add(LOOK.scale(range * PAST_THE_RANGE))));
        }

        @Test
        void aPointOutsideTheConeAngleLiesOutside() {
            Vec3 pastTheRim = StreamCone.launchDirection(LOOK, coneDegrees * PAST_THE_RIM, 1, 0);
            Vec3 point = HAND.add(pastTheRim.scale(range / 2));
            assertFalse(StreamCone.contains(HAND, LOOK, range, coneDegrees, point));
            assertFalse(vortex().bounds(point));
        }
    }
}
