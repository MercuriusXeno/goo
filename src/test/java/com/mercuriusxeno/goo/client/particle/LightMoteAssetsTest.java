package com.mercuriusxeno.goo.client.particle;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The light mote Laser's beam sheds ships a particle definition whose every
 * texture resolves to a sprite under assets/goo/textures/particle
 * (decision sunbeam-sheds-motes-rising-from-the-beam).
 */
class LightMoteAssetsTest {

    private static final String DEFINITION = "/assets/goo/particles/light_mote.json";
    private static final String NAMESPACE = "goo:";

    @Test
    void everyTextureTheDefinitionNamesShipsAsASprite() throws Exception {
        try (InputStream in = LightMoteAssetsTest.class.getResourceAsStream(DEFINITION)) {
            assertNotNull(in, "Particle definition missing on classpath");
            JsonObject definition = JsonParser.parseReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            assertFalse(definition.getAsJsonArray("textures").isEmpty(), "definition names no texture");
            for (JsonElement texture : definition.getAsJsonArray("textures")) {
                String sprite = "/assets/goo/textures/particle/"
                        + texture.getAsString().substring(NAMESPACE.length()) + ".png";
                try (InputStream png = LightMoteAssetsTest.class.getResourceAsStream(sprite)) {
                    assertNotNull(png, sprite + " missing on classpath");
                }
            }
        }
    }
}
