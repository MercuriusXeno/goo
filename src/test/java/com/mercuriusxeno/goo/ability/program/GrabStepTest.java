package com.mercuriusxeno.goo.ability.program;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests Grab's hold distance by pitch, its reach with and without
 * Telekinesis, and which left clicks throw through it
 * (decisions grab-holds-and-throws-a-physics-body, grab-reaches-telekinesis-range).
 */
class GrabStepTest {

    private static final double NEAR = 2;
    private static final double FAR = 4;
    private static final double EPSILON = 1e-6;
    private static final GrabStep GRAB = new GrabStep(8, NEAR, FAR, 1.5);

    @Nested
    class HoldDistance {

        @Test
        void levelOrUpwardLookHoldsAtFar() {
            assertEquals(FAR, GrabStep.holdDistance(0f, NEAR, FAR), EPSILON);
            assertEquals(FAR, GrabStep.holdDistance(-45f, NEAR, FAR), EPSILON);
        }

        @Test
        void downwardLookDrawsInLinearlyToNear() {
            assertEquals(3, GrabStep.holdDistance(GrabStep.FULL_DRAW_PITCH / 2, NEAR, FAR), EPSILON);
            assertEquals(NEAR, GrabStep.holdDistance(GrabStep.FULL_DRAW_PITCH, NEAR, FAR), EPSILON);
            assertEquals(NEAR, GrabStep.holdDistance(90f, NEAR, FAR), EPSILON);
        }
    }

    @Nested
    class Reach {

        private static final double TELEKINESIS_REACH = 8;

        @Test
        void withoutTelekinesisReachesItsOwnRange() {
            assertEquals(8, GRAB.reachWith(null), EPSILON);
        }

        @Test
        void telekinesisAddsItsReachToTheRange() {
            assertEquals(16, GRAB.reachWith(laid(TELEKINESIS_REACH)), EPSILON);
        }

        @Test
        void shippedGrabReachesSixteenAloneAndTwentyFourWithShippedTelekinesis() {
            GrabStep shipped = GrabStep.CODEC.codec().parse(JsonOps.INSTANCE,
                    firstBehavior("kinetic_grab.json")).getOrThrow();
            TelekinesisStep telekinesis = TelekinesisStep.CODEC.codec().parse(JsonOps.INSTANCE,
                    firstBehavior("kinetic_telekinesis.json")).getOrThrow();
            assertEquals(16, shipped.reachWith(null), EPSILON);
            assertEquals(24, shipped.reachWith(laid(telekinesis.reach())), EPSILON);
        }

        @Test
        void aGrabAbilityAimsAsFarAsGrabReaches() {
            assertEquals(16, GrabStep.reachOf(List.of(new AncientStep(), GRAB), laid(TELEKINESIS_REACH))
                    .orElseThrow(), EPSILON);
        }

        @Test
        void anAbilityWithoutGrabNamesNoReach() {
            assertTrue(GrabStep.reachOf(List.of(new AncientStep()), null).isEmpty());
        }

        private static AttributeModifier laid(double reach) {
            return new AttributeModifier(Identifier.fromNamespaceAndPath("goo", "telekinesis_entity_reach"),
                    reach, AttributeModifier.Operation.ADD_VALUE);
        }

        private static JsonObject firstBehavior(String file) {
            String path = "/data/goo/goo_abilities/" + file;
            try (InputStream in = GrabStepTest.class.getResourceAsStream(path)) {
                assertNotNull(in, "classpath should hold " + path);
                JsonObject ability = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                return ability.getAsJsonArray("behaviors").get(0).getAsJsonObject();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    @Nested
    class ThrowsOnAttack {

        @Test
        void armedPressOnAGrabThrows() {
            assertTrue(GrabStep.throwsOnAttack(true, List.of(GRAB)));
        }

        @Test
        void unarmedPressOrAnotherAbilityPunches() {
            assertFalse(GrabStep.throwsOnAttack(false, List.of(GRAB)));
            assertFalse(GrabStep.throwsOnAttack(true, List.of(new AncientStep())));
        }
    }
}
