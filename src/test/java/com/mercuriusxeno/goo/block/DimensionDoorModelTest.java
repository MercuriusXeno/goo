package com.mercuriusxeno.goo.block;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the room door's block model: it names the particle texture its
 * breaking motes wear and holds no element, so the end portal starfield
 * its renderer draws is all the doorway shows, in the room and in the
 * room's copies (decision room-door-wears-the-end-portal-starfield).
 */
class DimensionDoorModelTest {

    private static final String MODEL = "/assets/goo/models/block/dimension_door.json";

    @Test
    void theModelNamesAParticleTextureAndHoldsNoElement() throws Exception {
        JsonObject model = loadModel();

        JsonObject textures = model.getAsJsonObject("textures");
        assertNotNull(textures, "the model names its textures");
        assertEquals("goo:block/dimension_door", textures.get("particle").getAsString(), "the particle texture");
        assertTrue(!model.has("elements") || model.getAsJsonArray("elements").isEmpty(),
                "the model holds no element: " + model.get("elements"));
    }

    private static JsonObject loadModel() throws Exception {
        try (InputStream in = DimensionDoorModelTest.class.getResourceAsStream(MODEL)) {
            assertNotNull(in, MODEL + " on the classpath");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
