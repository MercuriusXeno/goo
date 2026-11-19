package com.mercuriusxeno.goo.lunar;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Xeno gathers on the dark side: goo:dark_side_spread answers its dark-side spread for a chunk past the light side and
 * its light-side spread at x = 0, and nests and monoliths name a tighter spacing for the dark side; goo:dark_side_count
 * answers its dark-side count past the light side and its light-side count at x = 0.
 * decision dark-side-is-an-endless-night-of-eldritch-rich-in-void-and-xeno
 */
class SidedTest {

    private static final int LIGHT_SIDE_COUNT = 1;
    private static final int DARK_SIDE_COUNT = 4;
    private static final Sided.Spread LIGHT_SIDE_SPREAD = new Sided.Spread(20, 8);
    private static final Sided.Spread DARK_SIDE_SPREAD = new Sided.Spread(10, 4);
    /** The first chunk wholly past the light side: its blocks start at x 3,750,016. */
    private static final int DARK_SIDE_CHUNK_X = 234_376;
    private static final int LIGHT_SIDE_CHUNK_X = 0;
    private static final double DARK_SIDE_X = 3_750_001;
    private static final double LIGHT_SIDE_X = 0;

    private static JsonObject resourceJson(String path) throws IOException {
        try (InputStream stream = SidedTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, path + " is not on the classpath");
            return JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Nested
    class TheSpread {

        private final Sided<Sided.Spread> spread = new Sided<>(LIGHT_SIDE_SPREAD, DARK_SIDE_SPREAD);

        @Test
        void aChunkPastTheLightSideTakesTheDarkSidesSpread() {
            assertEquals(DARK_SIDE_SPREAD, spread.atChunk(DARK_SIDE_CHUNK_X));
        }

        @Test
        void aChunkAtXZeroTakesTheLightSidesSpread() {
            assertEquals(LIGHT_SIDE_SPREAD, spread.atChunk(LIGHT_SIDE_CHUNK_X));
        }

        @ParameterizedTest
        @ValueSource(strings = {"eldritch_nests", "monoliths"})
        void eachXenoLandmarkSitsCloserTogetherOnTheDarkSide(String set) throws IOException {
            JsonObject placement = resourceJson("/data/goo/worldgen/structure_set/" + set + ".json")
                    .getAsJsonObject("placement");
            assertEquals("goo:dark_side_spread", placement.get("type").getAsString());
            int light = placement.getAsJsonObject("light_side").get("spacing").getAsInt();
            int dark = placement.getAsJsonObject("dark_side").get("spacing").getAsInt();
            assertTrue(dark < light, set + " spaces " + dark + " chunks apart on the dark side, " + light
                    + " on the light side");
        }
    }

    @Nested
    class TheCount {

        private final Sided<Integer> count = new Sided<>(LIGHT_SIDE_COUNT, DARK_SIDE_COUNT);

        @Test
        void aPositionPastTheLightSideTakesTheDarkSidesCount() {
            assertEquals(DARK_SIDE_COUNT, count.at(DARK_SIDE_X));
        }

        @Test
        void aPositionAtXZeroTakesTheLightSidesCount() {
            assertEquals(LIGHT_SIDE_COUNT, count.at(LIGHT_SIDE_X));
        }
    }
}
