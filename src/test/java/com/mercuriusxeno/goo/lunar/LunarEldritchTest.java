package com.mercuriusxeno.goo.lunar;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The dark side is an endless night of eldritch: the monstrosity spawns under its open sky at every hour, where the
 * light side's open ground spawns it by night alone, and the dark side spawns it in larger groups by the factor the
 * dark_side_eldritch data map names for lunar's biome.
 * decision dark-side-is-an-endless-night-of-eldritch-rich-in-void-and-xeno
 */
class LunarEldritchTest {

    /** The sky darkening lunar_day's timeline gives at noon. */
    private static final int NOON_DARKEN = 0;
    private static final double DARK_SIDE_X = 5_000_000;
    private static final double LIGHT_SIDE_X = 0;
    private static final boolean UNDER_THE_SKY = true;
    private static final String DATA_MAP = "/data/goo/data_maps/worldgen/biome/dark_side_eldritch.json";
    private static final String LUNAR_BIOME = "goo:lunar";
    private static final String LUNAR_BIOME_FILE = "/data/goo/worldgen/biome/lunar.json";

    private static JsonObject resourceJson(String path) throws IOException {
        try (InputStream stream = LunarEldritchTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, path + " is not on the classpath");
            return JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Nested
    class TheSpawnHour {

        @Test
        void theDarkSidesOpenGroundSpawnsTheEldritchAtNoon() {
            assertTrue(LunarEldritch.spawnHourAllows(DARK_SIDE_X, NOON_DARKEN, UNDER_THE_SKY));
        }

        @Test
        void theLightSidesOpenGroundRefusesTheEldritchAtNoon() {
            assertFalse(LunarEldritch.spawnHourAllows(LIGHT_SIDE_X, NOON_DARKEN, UNDER_THE_SKY));
        }
    }

    @Nested
    class TheVolume {

        @Test
        void theDarkSideSpawnsLargerGroupsThanTheLightSideByTheDatasFactor() throws IOException {
            int factor = resourceJson(DATA_MAP).getAsJsonObject("values").getAsJsonObject(LUNAR_BIOME)
                    .get(LunarEldritch.GROUP_FACTOR_FIELD).getAsInt();
            int lightSideMax = resourceJson(LUNAR_BIOME_FILE).getAsJsonObject("spawners").getAsJsonArray("monster")
                    .get(0).getAsJsonObject().get("maxCount").getAsInt();
            int darkSide = LunarEldritch.volumeAt(DARK_SIDE_X, lightSideMax, factor);
            int lightSide = LunarEldritch.volumeAt(LIGHT_SIDE_X, lightSideMax, factor);
            assertTrue(darkSide > lightSide, "the dark side spawns up to " + darkSide + ", the light side "
                    + lightSide);
        }
    }
}
