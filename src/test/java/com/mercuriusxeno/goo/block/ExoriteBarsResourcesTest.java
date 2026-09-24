package com.mercuriusxeno.goo.block;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bundled exorite bars resources (decision exorite-bars-retextured-iron-bars):
 * en_us.json names the block, and every bar model its blockstate names draws
 * the exorite bars texture, which ships under textures/block.
 */
class ExoriteBarsResourcesTest {

    private static final String LANG = "/assets/goo/lang/en_us.json";
    private static final String BLOCKSTATE = "/assets/goo/blockstates/exorite_bars.json";
    private static final String TEXTURE_ID = "goo:block/exorite_bars";
    private static final String TEXTURE_FILE = "/assets/goo/textures/block/exorite_bars.png";
    private static final String GOO_BLOCK_MODEL = "goo:block/";
    private static final String MODEL_DIR = "/assets/goo/models/block/";
    private static final int IRON_BARS_MODEL_COUNT = 6;

    @Test
    void langNamesTheBlock() throws Exception {
        assertTrue(readJson(LANG).has("block.goo.exorite_bars"));
    }

    @Test
    void everyBlockstateModelDrawsTheExoriteTexture() throws Exception {
        Set<String> models = new HashSet<>();
        for (JsonElement part : readJson(BLOCKSTATE).getAsJsonArray("multipart")) {
            models.add(part.getAsJsonObject().getAsJsonObject("apply").get("model").getAsString());
        }
        assertEquals(IRON_BARS_MODEL_COUNT, models.size(), "the blockstate should name iron bars' six models");
        for (String model : models) {
            assertTrue(model.startsWith(GOO_BLOCK_MODEL), model + " should be a goo model");
            JsonObject textures = readJson(MODEL_DIR + model.substring(GOO_BLOCK_MODEL.length()) + ".json")
                    .getAsJsonObject("textures");
            assertFalse(textures.isEmpty(), model + " should name textures");
            textures.entrySet().forEach(texture ->
                    assertEquals(TEXTURE_ID, texture.getValue().getAsString(), model + " " + texture.getKey()));
        }
    }

    @Test
    void textureShipsUnderTexturesBlock() throws Exception {
        try (InputStream in = ExoriteBarsResourcesTest.class.getResourceAsStream(TEXTURE_FILE)) {
            assertNotNull(in, "Texture missing on classpath: " + TEXTURE_FILE);
        }
    }

    private static JsonObject readJson(String path) throws Exception {
        try (InputStream in = ExoriteBarsResourcesTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "Resource missing on classpath: " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
