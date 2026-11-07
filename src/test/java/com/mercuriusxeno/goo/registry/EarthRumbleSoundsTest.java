package com.mercuriusxeno.goo.registry;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mod ships its own dull earthquake rumble, named in sounds.json as
 * effects.earth_rumble and resolving to the generated ogg
 * (decision spire-rumbles-like-a-dull-earthquake).
 */
class EarthRumbleSoundsTest {

    private static final String SOUNDS_JSON = "/assets/goo/sounds.json";
    private static final String RUMBLE_OGG = "/assets/goo/sounds/effects/earth_rumble.ogg";
    /** An Ogg stream opens with its capture pattern. */
    private static final byte[] OGG_CAPTURE = {'O', 'g', 'g', 'S'};

    @Test
    void soundsJsonNamesTheRumbleUnderItsEffectKey() throws IOException {
        try (InputStream in = EarthRumbleSoundsTest.class.getResourceAsStream(SOUNDS_JSON)) {
            assertNotNull(in, SOUNDS_JSON + " is not on the classpath");
            JsonObject sounds = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            assertTrue(sounds.has("effects.earth_rumble"), "sounds.json names no effects.earth_rumble");
            assertEquals("goo:effects/earth_rumble", sounds.getAsJsonObject("effects.earth_rumble")
                    .getAsJsonArray("sounds").get(0).getAsJsonObject().get("name").getAsString());
        }
    }

    @Test
    void theRumbleResolvesToAnOggOnTheClasspath() throws IOException {
        try (InputStream in = EarthRumbleSoundsTest.class.getResourceAsStream(RUMBLE_OGG)) {
            assertNotNull(in, RUMBLE_OGG + " is not on the classpath");
            byte[] head = in.readNBytes(OGG_CAPTURE.length);
            assertEquals(new String(OGG_CAPTURE, StandardCharsets.US_ASCII),
                    new String(head, StandardCharsets.US_ASCII), "the rumble is no Ogg stream");
        }
    }
}
