package com.mercuriusxeno.goo.lunar;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The dark side is rich in eldritch: the monstrosity spawns under the open sky by night alone, on either half of the
 * moon, and the dark side spawns it in larger groups by the factor the dark_side_eldritch data map names for lunar's
 * surface biomes.
 * decision dark-side-is-an-endless-night-of-eldritch-rich-in-void-and-xeno
 * decision sun-follows-the-phase-and-lights-the-far-side-too
 */
class LunarEldritchTest {

    private static final double DARK_SIDE_X = 5_000_000;
    private static final double LIGHT_SIDE_X = 0;
    private static final boolean UNDER_THE_SKY = true;
    private static final String DATA_MAP = "/data/goo/data_maps/worldgen/biome/dark_side_eldritch.json";

    private static JsonObject resourceJson(String path) throws IOException {
        try (InputStream stream = LunarEldritchTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, path + " is not on the classpath");
            return JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Nested
    class TheSpawnHour {

        private static final double OVERHEAD = 0;
        private static final double UNDERFOOT = 180;

        @ParameterizedTest
        @ValueSource(doubles = {LIGHT_SIDE_X, DARK_SIDE_X})
        void theOpenGroundSpawnsTheEldritchAtItsMidnight(double x) {
            assertTrue(LunarEldritch.spawnHourAllows(x, LunarNightTest.clockWhenTheSunStandsAt(x, UNDERFOOT),
                    UNDER_THE_SKY));
        }

        @ParameterizedTest
        @ValueSource(doubles = {LIGHT_SIDE_X, DARK_SIDE_X})
        void theOpenGroundRefusesTheEldritchAtItsNoon(double x) {
            assertFalse(LunarEldritch.spawnHourAllows(x, LunarNightTest.clockWhenTheSunStandsAt(x, OVERHEAD),
                    UNDER_THE_SKY));
        }

        @ParameterizedTest
        @ValueSource(doubles = {LIGHT_SIDE_X, DARK_SIDE_X})
        void coverSpawnsTheEldritchAtItsNoon(double x) {
            assertTrue(LunarEldritch.spawnHourAllows(x, LunarNightTest.clockWhenTheSunStandsAt(x, OVERHEAD),
                    !UNDER_THE_SKY));
        }
    }

    @Nested
    class TheVolume {

        @ParameterizedTest
        @ValueSource(strings = {"lunar_maria", "lunar_highlands", "lunar_shadowed_basins", "lunar_eldritch_ground"})
        void theDarkSideSpawnsLargerGroupsThanTheLightSideByTheDatasFactor(String biome) throws IOException {
            JsonObject factorEntry = resourceJson(DATA_MAP).getAsJsonObject("values").getAsJsonObject("goo:" + biome);
            assertNotNull(factorEntry, "the dark_side_eldritch data map names no factor for goo:" + biome);
            int factor = factorEntry.get(LunarEldritch.GROUP_FACTOR_FIELD).getAsInt();
            int lightSideMax = BiomeSpawnJson.maxCount(BiomeSpawnJson.monsterSpawners(
                    resourceJson("/data/goo/worldgen/biome/" + biome + ".json")).get(0).getAsJsonObject());
            int darkSide = LunarEldritch.volumeAt(DARK_SIDE_X, lightSideMax, factor);
            int lightSide = LunarEldritch.volumeAt(LIGHT_SIDE_X, lightSideMax, factor);
            assertTrue(darkSide > lightSide, "the dark side spawns up to " + darkSide + ", the light side "
                    + lightSide);
        }
    }
}
