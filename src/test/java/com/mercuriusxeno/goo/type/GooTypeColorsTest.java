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
 * through the classpath, read apart from the types they sat on, that xeno
 * wears quantum's former purples, and that yore's fluid is aeon's oil slick.
 */
class GooTypeColorsTest {

    private static final String TYPE_JSON_FORMAT = "/data/goo/goo/goo_type/%s.json";
    private static final String FLUID_TYPES_JSON = "/data/goo/goo_fluid_types.json";
    private static final List<String> CHANNELS = List.of("wheel", "bright", "highlight", "edge");
    private static final long AEON_SEED = 40_975L;
    private static final int AEON_FRAMETIME = 5;
    private static final float AEON_VISCOSITY = 2.5f;
    private static final String ICON_BLUE = "3AA0E0";
    private static final String ICON_ORANGE = "E8603C";
    private static final float ICON_HUE_TOLERANCE = 15f;
    private static final float ICON_SATURATION = 0.4f;

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

    @Test
    void yoreWearsAeonsOilSlickFluid() throws IOException {
        // yore-gets-opalescent-iridescence-back: aeon's fluid config as it stood before 8b83cec5
        JsonObject yore = fluidEntry("yore");
        assertEquals(List.of("E898B8", "F0E0B0", "88D0F0", "F8E0D0", "A0F0C0", "D8B8F8", "F8D898", "F8F0E8"),
                paletteStops("yore"));
        assertEquals(AEON_SEED, yore.get("seed").getAsLong(), "yore's seed");
        assertEquals(AEON_FRAMETIME, yore.get("frametime").getAsInt(), "yore's frametime");
        assertEquals(AEON_VISCOSITY, yore.getAsJsonObject("genParams").get("viscosity").getAsFloat(),
                "yore's viscosity");
    }

    @Test
    void quantumWearsItsIconsBlueAndReddishOrange() throws IOException {
        // quantum-is-gray-half-white-half-black, restated: quantum uses the colors of its type icon
        assertTrue(hueDistance(channel("quantum", "wheel"), ICON_BLUE) <= ICON_HUE_TOLERANCE,
                "quantum's wheel " + channel("quantum", "wheel") + " is not the icon's blue");
        assertTrue(hueDistance(channel("quantum", "edge"), ICON_ORANGE) <= ICON_HUE_TOLERANCE,
                "quantum's edge " + channel("quantum", "edge") + " is not the icon's reddish orange");
        for (String name : CHANNELS) {
            int rgb = Integer.parseInt(channel("quantum", name), 16);
            float saturation = java.awt.Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null)[1];
            assertTrue(saturation >= ICON_SATURATION, "quantum's " + name + " is washed out at " + saturation);
        }
    }

    private static List<String> paletteStops(String type) throws IOException {
        List<String> stops = new java.util.ArrayList<>();
        fluidEntry(type).getAsJsonArray("palette").forEach(stop ->
                stops.add(stop.getAsJsonObject().get("color").getAsString().substring(1)));
        return stops;
    }

    private static JsonObject fluidEntry(String type) throws IOException {
        try (InputStream in = GooTypeColorsTest.class.getResourceAsStream(FLUID_TYPES_JSON)) {
            assertNotNull(in, "missing on the classpath: " + FLUID_TYPES_JSON);
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                for (var entry : JsonParser.parseReader(reader).getAsJsonArray()) {
                    JsonObject fluid = entry.getAsJsonObject();
                    if (type.equals(fluid.get("id").getAsString())) {
                        return fluid;
                    }
                }
            }
        }
        throw new AssertionError(type + " has no goo_fluid_types entry");
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
