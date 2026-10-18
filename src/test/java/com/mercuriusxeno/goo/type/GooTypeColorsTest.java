package com.mercuriusxeno.goo.type;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that the recolored goo types' UI channels, read from their type JSON
 * through the classpath, read apart from the types they sat on and that xeno
 * wears quantum's former purples.
 */
class GooTypeColorsTest {

    private static final String TYPE_JSON_FORMAT = "/data/goo/goo/goo_type/%s.json";
    private static final List<String> CHANNELS = List.of("wheel", "bright", "highlight", "edge");

    @Test
    void kineticWheelSitsFarFromJellys() throws IOException {
        // kinetic-is-electric-blue-and-white
        float distance = hueDistance(channel("kinetic", "wheel"), channel("jelly", "wheel"));
        assertTrue(distance >= 90f, "kinetic's wheel sits " + distance + " degrees from jelly's");
    }

    @Test
    void zooWheelSitsApartFromYores() throws IOException {
        // zoo-takes-leather-and-rabbit-hide-colors
        float distance = hueDistance(channel("zoo", "wheel"), channel("yore", "wheel"));
        assertTrue(distance >= 15f, "zoo's wheel sits " + distance + " degrees from yore's");
    }

    @Test
    void xenoWearsQuantumsFormerPurples() throws IOException {
        // xeno-takes-quantums-purple
        List<String> quantumsFormer = List.of("6A5ACD", "9282F5", "7969DC", "8878EB");
        for (int index = 0; index < CHANNELS.size(); index++) {
            assertEquals(quantumsFormer.get(index), channel("xeno", CHANNELS.get(index)),
                    "xeno's " + CHANNELS.get(index));
        }
    }

    static float hueDistance(String firstHex, String secondHex) {
        float gap = Math.abs(hue(firstHex) - hue(secondHex));
        return Math.min(gap, 360f - gap);
    }

    static float hue(String hex) {
        int rgb = Integer.parseInt(hex, 16);
        float[] hsb = java.awt.Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
        return hsb[0] * 360f;
    }

    private static String channel(String type, String name) throws IOException {
        String path = TYPE_JSON_FORMAT.formatted(type);
        try (InputStream in = GooTypeColorsTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing on the classpath: " + path);
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                return json.get(name).getAsString().toUpperCase(java.util.Locale.ROOT);
            }
        }
    }
}
