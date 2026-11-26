package com.mercuriusxeno.goo.lunar;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Lunar stands 288 blocks tall, from -128 to 160, and every depth band stands at half its old height: the dimension
 * type, lunar's noise settings and {@link LunarDepths} name the same span and bands.
 * decision moon-is-288-tall-half-of-today
 */
class LunarDepthsTest {

    private static final String DIMENSION_TYPE = "/data/goo/dimension_type/lunar.json";
    private static final String NOISE_SETTINGS = "/data/goo/worldgen/noise_settings/lunar.json";
    private static final int FLOOR_Y = -128;
    private static final int HEIGHT = 288;

    private static JsonObject resourceJson(String path) throws IOException {
        try (InputStream stream = LunarDepthsTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, path + " is not on the classpath");
            return JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Nested
    class WorldSpan {

        @Test
        void dimensionTypeSpansFromMinus128To160() throws IOException {
            JsonObject type = resourceJson(DIMENSION_TYPE);
            assertEquals(FLOOR_Y, type.get("min_y").getAsInt());
            assertEquals(HEIGHT, type.get("height").getAsInt());
            assertEquals(HEIGHT, type.get("logical_height").getAsInt());
        }

        @Test
        void lunarDepthsNamesTheDimensionTypesFloorAndHeight() {
            assertEquals(FLOOR_Y, LunarDepths.WORLD_FLOOR_Y);
            assertEquals(HEIGHT, LunarDepths.WORLD_HEIGHT);
        }

        @Test
        void noiseSettingsNameTheSameFloorAndHeight() throws IOException {
            JsonObject noise = resourceJson(NOISE_SETTINGS).getAsJsonObject("noise");
            assertEquals(FLOOR_Y, noise.get("min_y").getAsInt());
            assertEquals(HEIGHT, noise.get("height").getAsInt());
        }
    }

    @Nested
    class Bands {

        @Test
        void seaStandsAtMinus88() {
            assertEquals(-88, LunarDepths.SEA_LEVEL_Y);
        }

        @Test
        void seaZoneTopsOutAtMinus64() {
            assertEquals(-64, LunarDepths.SEA_ZONE_TOP_Y);
        }

        @Test
        void warrensTopOutAtMinus32() {
            assertEquals(-32, LunarDepths.WARREN_ZONE_TOP_Y);
        }

        @Test
        void cavernsTopOutAtZero() {
            assertEquals(0, LunarDepths.CAVERN_ZONE_TOP_Y);
        }

        @Test
        void rubbleCavesTopOutAt20() {
            assertEquals(20, LunarDepths.SHALLOW_ZONE_TOP_Y);
        }
    }
}
