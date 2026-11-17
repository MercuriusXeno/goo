package com.mercuriusxeno.goo.registry;

import com.google.gson.JsonArray;
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
 * Lunar's rising tone: GooSounds registers it under the id GooSoundIds holds, and sounds.json names that id,
 * resolving to an ogg the mod ships, under a subtitle en_us.json names.
 * decision gate-fixates-your-view-on-the-moon-until-it-takes-you
 */
class LunarToneSoundTest {

    private static final String SOUNDS_JSON = "/assets/goo/sounds.json";
    private static final String EN_US_JSON = "/assets/goo/lang/en_us.json";
    private static final String GOO_NAMESPACE = "goo:";
    /** An Ogg stream opens with its capture pattern. */
    private static final String OGG_CAPTURE = "OggS";

    @Test
    void soundRegistryHoldsTheToneUnderItsId() {
        assertEquals(GooSoundIds.LUNAR_TONE, GooSounds.LUNAR_TONE.getId());
    }

    @Test
    void soundsJsonNamesTheToneResolvingToAShippedOgg() throws IOException {
        JsonObject entry = toneEntry();
        JsonArray variations = entry.getAsJsonArray("sounds");
        assertTrue(variations != null && !variations.isEmpty(), "the Lunar tone names no sound");
        for (int index = 0; index < variations.size(); index++) {
            String name = variations.get(index).getAsJsonObject().get("name").getAsString();
            assertTrue(name.startsWith(GOO_NAMESPACE), "the Lunar tone plays " + name + ", no sound the mod ships");
            String resource = "/assets/goo/sounds/" + name.substring(GOO_NAMESPACE.length()) + ".ogg";
            try (InputStream in = LunarToneSoundTest.class.getResourceAsStream(resource)) {
                assertNotNull(in, resource + " is not on the classpath");
                assertEquals(OGG_CAPTURE, new String(in.readNBytes(OGG_CAPTURE.length()), StandardCharsets.US_ASCII),
                        resource + " is no Ogg stream");
            }
        }
    }

    @Test
    void soundsJsonGivesTheToneASubtitleEnUsNames() throws IOException {
        JsonObject entry = toneEntry();
        assertTrue(entry.has("subtitle"), "the Lunar tone carries no subtitle");
        String subtitle = entry.get("subtitle").getAsString();
        assertTrue(readJson(EN_US_JSON).has(subtitle), "en_us.json names no " + subtitle);
    }

    private static JsonObject toneEntry() throws IOException {
        JsonObject sounds = readJson(SOUNDS_JSON);
        String path = GooSoundIds.LUNAR_TONE.getPath();
        assertTrue(sounds.has(path), "sounds.json names no " + path);
        return sounds.getAsJsonObject(path);
    }

    private static JsonObject readJson(String resource) throws IOException {
        try (InputStream in = LunarToneSoundTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, resource + " is not on the classpath");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
