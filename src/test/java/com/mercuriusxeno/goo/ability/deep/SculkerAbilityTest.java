package com.mercuriusxeno.goo.ability.deep;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deep's brew ships as deep_sculker.json, named goo.ability.deep.sculker,
 * badged brew and running a sculker step, read off the classpath
 * (decision dive-is-renamed-sculker).
 */
class SculkerAbilityTest {

    private static final String SCULKER_JSON = "data/goo/goo_abilities/deep_sculker.json";

    private static JsonObject sculker() throws IOException {
        try (InputStream in = SculkerAbilityTest.class.getClassLoader().getResourceAsStream(SCULKER_JSON)) {
            assertNotNull(in, SCULKER_JSON + " should ship");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    void shipsAsTheSculkerBrew() throws IOException {
        JsonObject sculker = sculker();
        assertEquals("goo.ability.deep.sculker", sculker.get("displayName").getAsString());
        assertEquals("brew", sculker.get("badge").getAsString());
    }

    @Test
    void runsASculkerStep() throws IOException {
        boolean runsSculker = false;
        for (JsonElement step : sculker().getAsJsonArray("behaviors")) {
            runsSculker |= "sculker".equals(step.getAsJsonObject().get("type").getAsString());
        }
        assertTrue(runsSculker, "deep_sculker.json should run a sculker step");
    }
}
