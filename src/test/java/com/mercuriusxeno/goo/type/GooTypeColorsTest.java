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
 * wears quantum's former purples, that yore's fluid is aeon's oil slick, and that quantum wears
 * its icon's blue and a brighter, oranger orange than its former red orange.
 */
class GooTypeColorsTest {

    private static final String TYPE_JSON_FORMAT = "/data/goo/goo/goo_type/%s.json";
    private static final String FLUID_TYPES_JSON = "/data/goo/goo_fluid_types.json";
    private static final List<String> CHANNELS = List.of("wheel", "bright", "highlight", "edge");
    private static final long AEON_SEED = 40_975L;
    private static final int AEON_FRAMETIME = 5;
    private static final float AEON_VISCOSITY = 2.5f;
    private static final String ICON_BLUE = "3AA0E0";
    private static final String ICON_HIGHLIGHT = "6AD0F0";
    private static final String ICON_ORANGE = "FF8A40";
    private static final List<String> FORMER_RED_ORANGES = List.of("F26E4B", "E8603C");
    private static final float ICON_HUE_TOLERANCE = 15f;
    private static final float ORANGE_STOP_HUE_TOLERANCE = 8f;
    private static final float ORANGE_HUE_CEILING = 45f;
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
    void quantumWearsItsIconsBlueAndBrighterOrange() throws IOException {
        // quantum-is-gray-half-white-half-black, restated: quantum uses the colors of its type icon
        assertEquals(ICON_BLUE, channel("quantum", "wheel"), "quantum's wheel");
        assertEquals(ICON_HIGHLIGHT, channel("quantum", "highlight"), "quantum's highlight");
        assertTrue(hueDistance(channel("quantum", "edge"), ICON_ORANGE) <= ICON_HUE_TOLERANCE,
                "quantum's edge " + channel("quantum", "edge") + " is not the icon's orange");
        for (String name : CHANNELS) {
            assertTrue(hsb(channel("quantum", name))[1] >= ICON_SATURATION, "quantum's " + name + " is washed out");
        }
    }

    @Test
    void quantumOrangeIsOrangerAndBrighterThanItsFormerRedOrange() throws IOException {
        // quantum-is-gray-half-white-half-black: a slightly brighter, oranger orange than its red orange
        for (String name : List.of("bright", "edge")) {
            String orange = channel("quantum", name);
            for (String former : FORMER_RED_ORANGES) {
                assertTrue(hue(orange) > hue(former), "quantum's " + name + " " + orange + " is no oranger than " + former);
                assertTrue(hsb(orange)[2] > hsb(former)[2], "quantum's " + name + " " + orange + " is no brighter than " + former);
            }
        }
    }

    @Test
    void quantumFluidOrangeStopsWearTheNewOrange() throws IOException {
        // quantum-is-gray-half-white-half-black
        String bright = channel("quantum", "bright");
        int orangeStops = 0;
        for (String stop : paletteStops("quantum")) {
            if (isOrange(stop)) {
                orangeStops++;
                assertTrue(hueDistance(stop, bright) <= ORANGE_STOP_HUE_TOLERANCE,
                        "quantum's fluid stop " + stop + " sits off its orange " + bright);
            }
        }
        assertTrue(orangeStops > 0, "quantum's fluid palette holds no orange stop");
    }

    private static boolean isOrange(String hex) {
        return hue(hex) <= ORANGE_HUE_CEILING && hsb(hex)[1] >= ICON_SATURATION;
    }

    private static float[] hsb(String hex) {
        int rgb = Integer.parseInt(hex, 16);
        return java.awt.Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
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
