package com.mercuriusxeno.goo.entity;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.registry.GooSoundIds;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A rising Build hunk loops the earth rumble for its whole climb, from its
 * first climbing tick until the rise ends or the hunk is removed
 * (decision spire-rumbles-like-a-dull-earthquake).
 */
class BuildRumbleTest {

    private static final int RISE_TICKS = 20;

    @Test
    void theLoopPlaysFromTheFirstClimbingTick() {
        assertTrue(BuildRumble.loopPlays(0, RISE_TICKS, true));
    }

    @Test
    void theLoopPlaysThroughTheClimb() {
        assertTrue(BuildRumble.loopPlays(RISE_TICKS - 1, RISE_TICKS, true));
    }

    @Test
    void theLoopStopsAtTheRisesEnd() {
        assertFalse(BuildRumble.loopPlays(RISE_TICKS, RISE_TICKS, true));
    }

    @Test
    void theLoopStopsWhenTheHunkIsRemoved() {
        assertFalse(BuildRumble.loopPlays(RISE_TICKS / 2, RISE_TICKS, false));
    }

    @Test
    void theLoopIsTheEarthRumbleSoundsJsonNames() throws IOException {
        assertEquals("goo:effects.earth_rumble", GooSoundIds.EARTH_RUMBLE.toString());
        try (InputStream in = BuildRumbleTest.class.getResourceAsStream("/assets/goo/sounds.json")) {
            assertNotNull(in, "sounds.json is not on the classpath");
            JsonObject sounds = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            assertTrue(sounds.has(GooSoundIds.EARTH_RUMBLE.getPath()), "sounds.json names no earth rumble");
        }
    }
}
