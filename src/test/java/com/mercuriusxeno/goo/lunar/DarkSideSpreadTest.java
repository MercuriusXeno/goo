package com.mercuriusxeno.goo.lunar;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Nests and monoliths sit closer together on the dark side: each one's structure set decodes through
 * goo:dark_side_spread, which places a chunk past x 3,750,000 by its dark-side spread and a chunk at x 0 by its
 * light-side spread, the dark side's spacing the tighter.
 * decision dark-side-is-an-endless-night-of-eldritch-rich-in-void-and-xeno
 */
class DarkSideSpreadTest {

    /** The first chunk wholly past the light side: its blocks start at x 3,750,016. */
    private static final int DARK_SIDE_CHUNK_X = 234_376;
    private static final int LIGHT_SIDE_CHUNK_X = 0;

    private static JsonObject placementOf(String set) throws IOException {
        String path = "/data/goo/worldgen/structure_set/" + set + ".json";
        try (InputStream stream = DarkSideSpreadTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, path + " is not on the classpath");
            return JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject()
                    .getAsJsonObject("placement");
        }
    }

    private static int spacing(JsonObject placement, String side) {
        return placement.getAsJsonObject(side).get("spacing").getAsInt();
    }

    @ParameterizedTest
    @ValueSource(strings = {"eldritch_nests", "monoliths"})
    void eachXenoLandmarkPlacesTighterOnTheDarkSideThanOnTheLightSide(String set) throws IOException {
        JsonObject placement = placementOf(set);
        DarkSideSpread spread = DarkSideSpread.CODEC.codec().parse(JsonOps.INSTANCE, placement).getOrThrow();
        int dark = spread.gridAt(DARK_SIDE_CHUNK_X).spacing();
        int light = spread.gridAt(LIGHT_SIDE_CHUNK_X).spacing();
        assertEquals(spacing(placement, "dark_side"), dark, set + " places a dark-side chunk by its dark side's spread");
        assertEquals(spacing(placement, "light_side"), light, set + " places a chunk at x 0 by its light side's spread");
        assertTrue(dark < light, set + " spaces " + dark + " chunks apart on the dark side, " + light
                + " on the light side");
    }
}
