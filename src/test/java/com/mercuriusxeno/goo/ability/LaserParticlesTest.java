package com.mercuriusxeno.goo.ability;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Laser's impact sheds the small flame and white smoke alone, its light
 * motes coming from the client's beam in place of end_rod sparks, and a
 * struck mob still bursts crit particles
 * (decision sunbeam-sheds-motes-rising-from-the-beam).
 */
class LaserParticlesTest {

    private static final String LASER_JSON = "data/goo/goo_abilities/glow_laser.json";

    private static JsonObject ray() throws IOException {
        try (InputStream in = LaserParticlesTest.class.getClassLoader().getResourceAsStream(LASER_JSON)) {
            assertNotNull(in, LASER_JSON + " should ship");
            JsonObject laser = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            return laser.getAsJsonArray("behaviors").get(0).getAsJsonObject();
        }
    }

    private static void gatherParticleIds(JsonElement element, Set<String> ids) {
        if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(child -> gatherParticleIds(child, ids));
        } else if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("type") && "particles".equals(object.get("type").getAsString())) {
                ids.add(object.get("id").getAsString());
            }
            object.entrySet().forEach(entry -> gatherParticleIds(entry.getValue(), ids));
        }
    }

    @Test
    void impactShedsTheSmallFlameAndWhiteSmokeAlone() throws IOException {
        Set<String> ids = new HashSet<>();
        gatherParticleIds(ray().get("impact"), ids);
        assertEquals(Set.of("minecraft:small_flame", "minecraft:white_smoke"), ids);
    }

    @Test
    void aStruckMobStillBurstsCrit() throws IOException {
        Set<String> ids = new HashSet<>();
        gatherParticleIds(ray().get("steps"), ids);
        assertTrue(ids.contains("minecraft:crit"), "the ray's steps should burst crit, held " + ids);
    }
}
