package com.mercuriusxeno.goo.type;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that the recolored goo types' UI channels, read from their type JSON
 * through the classpath, read apart from the types they sat on, that xeno
 * wears quantum's former purples, that nether wears the obsidian flesh palette the operator
 * picked, that infinity's fluid and channels wear palette B's oil slick and its icon is pearl,
 * and that quantum wears
 * its icon's blue and a brighter, oranger orange than its former red orange.
 */
class GooTypeColorsTest {

    private static final String TYPE_JSON_FORMAT = "/data/goo/goo/goo_type/%s.json";
    private static final String FLUID_TYPES_JSON = "/data/goo/goo_fluid_types.json";
    private static final List<String> CHANNELS = List.of("wheel", "bright", "highlight", "edge");
    private static final String PALETTE_B_OIL_SLICK_FLUID = """
            {"seed": 40975,
             "genParams": {"viscosity": 2.5, "decayRate": 0.015, "ignitionChance": 0.003, "ignitionStrength": 0.7,
                           "potHeatRate": 0.005, "potInfluence": 0.9, "neighborhoodReach": 1},
             "frametime": 5, "shadowHue": -1,
             "palette": [{"position": 0.0, "color": "#E070A8"}, {"position": 0.15, "color": "#F0C870"},
                         {"position": 0.3, "color": "#50B8F0"}, {"position": 0.45, "color": "#F0B898"},
                         {"position": 0.6, "color": "#60E8A0"}, {"position": 0.75, "color": "#B888F8"},
                         {"position": 0.9, "color": "#F8C060"}, {"position": 1.0, "color": "#FFF6EC"}]}
            """;
    private static final int PEARL_CYCLE_TICKS = 160;
    private static final float PEARL_SWING_DEGREES = 240f;
    private static final String INFINITY_ICON = "/assets/goo/textures/goo/type/infinity.png";
    private static final List<String> ICON_OUTLINE = List.of("393861", "1E1D39");
    private static final float PEARL_SATURATION_CEILING = 0.35f;
    private static final float PEARL_BRIGHTNESS_FLOOR = 0.6f;
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

    /**
     * Dire's bright is teal, green and blue each above red.
     * decision dire-is-ominous-trial-teal-black
     */
    @Test
    void direBrightIsTeal() throws IOException {
        int bright = Integer.parseInt(channel("dire", "bright"), 16);
        int red = bright >> 16 & 0xFF;
        assertTrue((bright >> 8 & 0xFF) > red && (bright & 0xFF) > red,
                () -> "dire's bright is not teal: " + Integer.toHexString(bright));
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
    void netherWearsTheObsidianFleshPalette() throws IOException {
        // nether-is-a-deeper-maroon-mixed-with-obsidians-colors: the operator's pick "A, obsidian flesh"
        List<String> obsidianFlesh = List.of("5C1C2A", "8C3A48", "6A4650", "2E1C44");
        for (int index = 0; index < CHANNELS.size(); index++) {
            assertEquals(obsidianFlesh.get(index), channel("nether", CHANNELS.get(index)),
                    "nether's " + CHANNELS.get(index));
        }
    }

    @Test
    void sculkWearsTheSculkColorsDeepWore() throws IOException {
        // sculk-takes-deeps-coloration-and-shrooms-goes
        List<String> deepsSculkColors = List.of("0F2430", "29DFEB", "16303E", "1FC8D8");
        for (int index = 0; index < CHANNELS.size(); index++) {
            assertEquals(deepsSculkColors.get(index), channel("sculk", CHANNELS.get(index)),
                    "sculk's " + CHANNELS.get(index));
        }
    }

    @Test
    void infinityWearsPaletteBOilSlickFluid() throws IOException {
        // infinity-swaps-to-aeons-good-opalescent-oil-slick: aeon's seed and motion, aeon's hue order with chroma raised
        JsonObject infinity = fluidEntry("infinity").deepCopy();
        infinity.remove("id");
        assertEquals(JsonParser.parseString(PALETTE_B_OIL_SLICK_FLUID), infinity, "infinity's fluid config");
    }

    @Test
    void infinityTypeJsonWearsPaletteBSheen() throws IOException {
        // infinity-swaps-to-aeons-good-opalescent-oil-slick
        List<String> paletteBSheen = List.of("C8A0F0", "F8E8FF", "F0B0D8", "A0E8D8");
        for (int index = 0; index < CHANNELS.size(); index++) {
            assertEquals(paletteBSheen.get(index), channel("infinity", CHANNELS.get(index)),
                    "infinity's " + CHANNELS.get(index));
        }
        JsonObject infinity = typeJson("infinity");
        assertEquals(PEARL_CYCLE_TICKS, infinity.get(HueCycle.HUE_CYCLE_TICKS).getAsInt(), "infinity's hue cycle");
        assertEquals(PEARL_SWING_DEGREES, infinity.get(HueCycle.HUE_SWING_DEGREES).getAsFloat(), "infinity's swing");
        assertEquals(1, infinity.get("vessel_source_per_mb").getAsInt(), "infinity's vessel source rate");
    }

    @Test
    void infinityIconInsideIsPearlWithNoBands() throws IOException {
        // infinity-swaps-to-aeons-good-opalescent-oil-slick: the lemniscate is pearl sheen, no rainbow bands
        BufferedImage icon;
        try (InputStream in = GooTypeColorsTest.class.getResourceAsStream(INFINITY_ICON)) {
            assertNotNull(in, "missing on the classpath: " + INFINITY_ICON);
            icon = ImageIO.read(in);
        }
        int pearlPixels = 0;
        for (int y = 0; y < icon.getHeight(); y++) {
            for (int x = 0; x < icon.getWidth(); x++) {
                int argb = icon.getRGB(x, y);
                String hex = "%06X".formatted(argb & 0xFFFFFF);
                if ((argb >>> 24) == 0 || ICON_OUTLINE.contains(hex)) {
                    continue;
                }
                float[] shade = hsb(hex);
                assertTrue(shade[1] <= PEARL_SATURATION_CEILING, "icon pixel " + x + "," + y + " " + hex + " is too saturated");
                assertTrue(shade[2] >= PEARL_BRIGHTNESS_FLOOR, "icon pixel " + x + "," + y + " " + hex + " is too dark");
                pearlPixels++;
            }
        }
        assertTrue(pearlPixels > 0, "the icon holds no pixel inside its outline");
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
        return typeJson(type).get(name).getAsString().toUpperCase(java.util.Locale.ROOT);
    }

    private static JsonObject typeJson(String type) throws IOException {
        String path = TYPE_JSON_FORMAT.formatted(type);
        try (InputStream in = GooTypeColorsTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing on the classpath: " + path);
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
    }
}
