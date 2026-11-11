package com.mercuriusxeno.goo.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mod ships its own three portal sounds, the placement zap, the
 * stepping-through whoosh and the standing hum, each named in sounds.json
 * under the id GooSoundIds holds, resolving to a generated ogg and carrying a
 * subtitle en_us.json names.
 * decision portal-placement-plays-a-zap-that-opens-into-a-hum
 * decision stepping-through-plays-a-soft-whoosh-at-both-ends
 * decision standing-portals-loop-a-soft-ambient-hum
 */
class PortalSoundsTest {

    private static final String SOUNDS_JSON = "/assets/goo/sounds.json";
    private static final String EN_US_JSON = "/assets/goo/lang/en_us.json";
    private static final String GOO_NAMESPACE = "goo:";
    /** An Ogg stream opens with its capture pattern. */
    private static final byte[] OGG_CAPTURE = {'O', 'g', 'g', 'S'};

    static Stream<Identifier> portalSoundIds() {
        return Stream.of(GooSoundIds.PORTAL_PLACE, GooSoundIds.PORTAL_CROSS, GooSoundIds.PORTAL_HUM);
    }

    @ParameterizedTest
    @MethodSource("portalSoundIds")
    void eachPortalSoundResolvesToAnOggOnTheClasspath(Identifier id) throws IOException {
        JsonObject entry = soundEntry(id);
        JsonArray variations = entry.getAsJsonArray("sounds");
        assertTrue(variations.size() > 0, id + " names no sound");
        for (int index = 0; index < variations.size(); index++) {
            String name = variations.get(index).getAsJsonObject().get("name").getAsString();
            assertTrue(name.startsWith(GOO_NAMESPACE), id + " plays " + name + ", no sound the mod ships");
            assertOgg("/assets/goo/sounds/" + name.substring(GOO_NAMESPACE.length()) + ".ogg");
        }
    }

    @ParameterizedTest
    @MethodSource("portalSoundIds")
    void eachPortalSoundCarriesASubtitleEnUsNames(Identifier id) throws IOException {
        JsonObject entry = soundEntry(id);
        assertTrue(entry.has("subtitle"), id + " carries no subtitle");
        String subtitle = entry.get("subtitle").getAsString();
        assertTrue(readJson(EN_US_JSON).has(subtitle), "en_us.json names no " + subtitle);
    }

    /** The hum is soft and falls away close by (decision standing-portals-loop-a-soft-ambient-hum). */
    @org.junit.jupiter.api.Test
    void theHumIsQuietAndFallsAwayCloseBy() throws IOException {
        JsonArray variations = soundEntry(GooSoundIds.PORTAL_HUM).getAsJsonArray("sounds");
        for (int index = 0; index < variations.size(); index++) {
            JsonObject sound = variations.get(index).getAsJsonObject();
            assertTrue(sound.has("volume") && sound.get("volume").getAsFloat() < 1f,
                    "the hum plays at full volume, " + sound);
            assertTrue(sound.has("attenuation_distance"), "the hum carries the default sixteen blocks, " + sound);
        }
    }

    private static JsonObject soundEntry(Identifier id) throws IOException {
        assertEquals("goo", id.getNamespace(), id + " sits outside the mod's namespace");
        JsonObject sounds = readJson(SOUNDS_JSON);
        assertTrue(sounds.has(id.getPath()), "sounds.json names no " + id.getPath());
        return sounds.getAsJsonObject(id.getPath());
    }

    private static JsonObject readJson(String resource) throws IOException {
        try (InputStream in = PortalSoundsTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, resource + " is not on the classpath");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static void assertOgg(String resource) throws IOException {
        try (InputStream in = PortalSoundsTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, resource + " is not on the classpath");
            byte[] head = in.readNBytes(OGG_CAPTURE.length);
            assertEquals(new String(OGG_CAPTURE, StandardCharsets.US_ASCII),
                    new String(head, StandardCharsets.US_ASCII), resource + " is no Ogg stream");
        }
    }
}
