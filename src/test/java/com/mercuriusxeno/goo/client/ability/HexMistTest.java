package com.mercuriusxeno.goo.client.ability;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.program.HexMistStep;
import com.mercuriusxeno.goo.ability.program.WindStep;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers Drain's hex mist: every spawn lies on the flat plane through the
 * hand inside the stream's range and cone and drifts at the hand, and the
 * held tick requests the mist for any stream naming it with no target given.
 */
class HexMistTest {

    private static final Vec3 HAND = new Vec3(10.5, 64.3, -3.25);
    private static final Vec3 AIM = new Vec3(0.6, 0, 0.8);
    private static final double RANGE = 4;
    private static final double CONE = 70;
    private static final double HALF_CONE = 35;
    private static final int DRAWS = 5000;
    private static final double TOLERANCE = 1e-9;

    @Nested
    class Spawns {

        @Test
        void everySpawnLiesFlatInsideTheConeAndDriftsAtTheHand() {
            RandomSource random = RandomSource.create(42L);
            for (int draw = 0; draw < DRAWS; draw++) {
                HexMist.MistSpawn spawn = HexMist.spawnAt(HAND, AIM, RANGE, CONE, draw, random);
                Vec3 out = spawn.at().subtract(HAND);
                assertEquals(HAND.y, spawn.at().y, TOLERANCE, spawn.toString());
                assertTrue(out.length() <= RANGE, spawn.toString());
                assertTrue(Math.toDegrees(Math.acos(out.normalize().dot(AIM))) <= HALF_CONE, spawn.toString());
                assertEquals(1, spawn.velocity().normalize().dot(out.scale(-1).normalize()), TOLERANCE,
                        spawn.toString());
            }
        }
    }

    @Nested
    class Dispatch {

        private static final String DRAIN_JSON = "/data/goo/goo_abilities/hex_drain.json";

        @Test
        void drainsShippedJsonNamesTheMist() throws IOException {
            try (InputStream in = HexMistTest.class.getResourceAsStream(DRAIN_JSON)) {
                assertNotNull(in, DRAIN_JSON);
                JsonObject drain = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                assertTrue(StreamSupport.stream(drain.getAsJsonArray("behaviors").spliterator(), false)
                        .map(JsonElement::getAsJsonObject)
                        .anyMatch(behavior -> "hex_mist".equals(behavior.get("type").getAsString())));
            }
        }

        @Test
        void aStreamNamingTheMistRequestsItWithNoTarget() {
            assertFalse(HexMist.spawnsFor(List.of(new HexMistStep()), HAND, AIM, RANGE, CONE, 0,
                    RandomSource.create(7L)).isEmpty());
        }

        @Test
        void aStreamNamingNoMistRequestsNone() {
            assertTrue(HexMist.spawnsFor(List.of(new WindStep(false)), HAND, AIM, RANGE, CONE, 0,
                    RandomSource.create(7L)).isEmpty());
        }
    }
}
