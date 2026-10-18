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
 * wears quantum's former purples, and that yore's sprite palette runs pale
 * across several hues.
 */
class GooTypeColorsTest {

    private static final String TYPE_JSON_FORMAT = "/data/goo/goo/goo_type/%s.json";
    private static final String FLUID_TYPES_JSON = "/data/goo/goo_fluid_types.json";
    private static final List<String> CHANNELS = List.of("wheel", "bright", "highlight", "edge");
    private static final float PALE_LIGHTNESS = 0.8f;
    private static final float TINTED_SATURATION = 0.06f;
    private static final int HUE_BUCKET_DEGREES = 30;
    private static final float BLACK_BODY = 0.2f;
    private static final float WHITE_BODY = 0.85f;
    private static final float BODY_SHARE = 0.25f;
    private static final float BODIES_TOGETHER = 0.85f;

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
    void yorePaletteStopsArePaleAcrossThreeHues() throws IOException {
        // yore-gets-opalescent-iridescence-back
        java.util.Set<Integer> hueBuckets = new java.util.TreeSet<>();
        for (String hex : paletteStops("yore")) {
            int rgb = Integer.parseInt(hex, 16);
            int red = (rgb >> 16) & 0xFF;
            int green = (rgb >> 8) & 0xFF;
            int blue = rgb & 0xFF;
            float lightness = (Math.max(red, Math.max(green, blue)) + Math.min(red, Math.min(green, blue))) / 510f;
            assertTrue(lightness >= PALE_LIGHTNESS, "yore's stop " + hex + " sits at lightness " + lightness);
            float[] hsb = java.awt.Color.RGBtoHSB(red, green, blue, null);
            if (hsb[1] >= TINTED_SATURATION) {
                hueBuckets.add(Math.round(hsb[0] * 360f / HUE_BUCKET_DEGREES) % (360 / HUE_BUCKET_DEGREES));
            }
        }
        assertTrue(hueBuckets.size() >= 3, "yore's stops tint only " + hueBuckets.size() + " hues");
    }

    @Test
    void quantumFluidSplitsIntoBodiesOfBlackAndWhite() throws IOException {
        // quantum-is-gray-half-white-half-black
        java.awt.image.BufferedImage strip;
        try (InputStream in = GooTypeColorsTest.class.getResourceAsStream("/assets/goo/textures/fluid/quantum_fluid.png")) {
            assertNotNull(in, "quantum's fluid sprite is missing");
            strip = javax.imageio.ImageIO.read(in);
        }
        int black = 0;
        int white = 0;
        int pixels = strip.getWidth() * strip.getHeight();
        for (int y = 0; y < strip.getHeight(); y++) {
            for (int x = 0; x < strip.getWidth(); x++) {
                int rgb = strip.getRGB(x, y);
                float value = java.awt.Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null)[2];
                black += value <= BLACK_BODY ? 1 : 0;
                white += value >= WHITE_BODY ? 1 : 0;
            }
        }
        assertTrue(black >= pixels * BODY_SHARE, "only " + black + " of " + pixels + " pixels are black");
        assertTrue(white >= pixels * BODY_SHARE, "only " + white + " of " + pixels + " pixels are white");
        assertTrue(black + white >= pixels * BODIES_TOGETHER,
                (pixels - black - white) + " of " + pixels + " pixels smear gray between the bodies");
    }

    private static List<String> paletteStops(String type) throws IOException {
        try (InputStream in = GooTypeColorsTest.class.getResourceAsStream(FLUID_TYPES_JSON)) {
            assertNotNull(in, "missing on the classpath: " + FLUID_TYPES_JSON);
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                for (var entry : JsonParser.parseReader(reader).getAsJsonArray()) {
                    JsonObject fluid = entry.getAsJsonObject();
                    if (type.equals(fluid.get("id").getAsString())) {
                        List<String> stops = new java.util.ArrayList<>();
                        fluid.getAsJsonArray("palette").forEach(stop ->
                                stops.add(stop.getAsJsonObject().get("color").getAsString().substring(1)));
                        return stops;
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
