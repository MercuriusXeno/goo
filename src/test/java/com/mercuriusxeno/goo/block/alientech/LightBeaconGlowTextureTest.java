package com.mercuriusxeno.goo.block.alientech;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The light beacon's bulb burns white to its column's yellow: every opaque texel its faces sample holds blue below
 * both red and green, so no teal or cyan pixel shows.
 * decision light-beacon-bulb-matches-its-column
 */
class LightBeaconGlowTextureTest {

    private static final String MODEL = "/assets/goo/models/block/light_beacon.json";
    private static final String GLOW = "/assets/goo/textures/block/light_beacon_glow.png";
    private static final String GLOW_TEXTURE = "#glow";

    @Test
    void everyBulbTexelHoldsBlueBelowRedAndGreen() throws IOException {
        BufferedImage glow = image(GLOW);
        int sampled = 0;
        for (JsonElement element : json(MODEL).getAsJsonArray("elements")) {
            for (JsonElement face : element.getAsJsonObject().getAsJsonObject("faces").asMap().values()) {
                JsonObject faceJson = face.getAsJsonObject();
                if (GLOW_TEXTURE.equals(faceJson.get("texture").getAsString())) {
                    sampled += assertWarm(glow, faceJson.getAsJsonArray("uv"));
                }
            }
        }
        assertFalse(sampled == 0, "The light beacon's model should hold a bulb wearing " + GLOW_TEXTURE);
    }

    private static int assertWarm(BufferedImage image, JsonArray uv) {
        int scale = image.getWidth() / 16;
        int left = (int) Math.min(uv.get(0).getAsDouble(), uv.get(2).getAsDouble()) * scale;
        int right = (int) Math.max(uv.get(0).getAsDouble(), uv.get(2).getAsDouble()) * scale;
        int top = (int) Math.min(uv.get(1).getAsDouble(), uv.get(3).getAsDouble()) * scale;
        int bottom = (int) Math.max(uv.get(1).getAsDouble(), uv.get(3).getAsDouble()) * scale;
        int sampled = 0;
        for (int y = top; y < bottom; y++) {
            for (int x = left; x < right; x++) {
                int argb = image.getRGB(x, y);
                int red = argb >> 16 & 0xFF;
                int green = argb >> 8 & 0xFF;
                int blue = argb & 0xFF;
                if ((argb >>> 24) > 0) {
                    assertTrue(blue < red && blue < green, String.format(
                            "The bulb texel at %d,%d reads %06X, its blue should stand below its red and green",
                            x, y, argb & 0xFFFFFF));
                    sampled++;
                }
            }
        }
        return sampled;
    }

    private static BufferedImage image(String path) throws IOException {
        try (InputStream in = LightBeaconGlowTextureTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path + " should be on the classpath");
            return ImageIO.read(in);
        }
    }

    private static JsonObject json(String path) throws IOException {
        try (InputStream in = LightBeaconGlowTextureTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path + " should be on the classpath");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
