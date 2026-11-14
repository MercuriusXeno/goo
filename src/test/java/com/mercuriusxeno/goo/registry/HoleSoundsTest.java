package com.mercuriusxeno.goo.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Void's hole sounds, each named in sounds.json under the id GooSoundIds
 * holds, listing files the mod ships and carrying a subtitle en_us.json
 * reads as naming the hole.
 * decision hole-opens-with-the-outsider-wub-pitched-up
 * decision hole-closes-with-the-outsider-wub-reversed-and-pitched-down
 */
class HoleSoundsTest {

    private static final String SOUNDS_JSON = "/assets/goo/sounds.json";
    private static final String EN_US_JSON = "/assets/goo/lang/en_us.json";
    private static final String GOO_NAMESPACE = "goo:";
    private static final String HOLE = "Hole";

    @Test
    void openingListsTheZoneRoomsTwoWubFiles() throws IOException {
        assertEquals(List.of("goo:effects/zone_wub", "goo:effects/zone_wub2"),
                filesOf(GooSoundIds.HOLE_OPEN));
    }

    @Test
    void openingsSubtitleNamesTheHole() throws IOException {
        assertSubtitleNamesTheHole(GooSoundIds.HOLE_OPEN);
    }

    /** tools/zone_sounds.py writes each as a shipped wub take's samples in reverse order. */
    @Test
    void closingListsEachShippedWubTakeReversed() throws IOException {
        assertEquals(List.of("goo:effects/zone_wub_reversed", "goo:effects/zone_wub2_reversed"),
                filesOf(GooSoundIds.HOLE_CLOSE));
    }

    @Test
    void closingsSubtitleNamesTheHole() throws IOException {
        assertSubtitleNamesTheHole(GooSoundIds.HOLE_CLOSE);
    }

    /** The files a sound lists, each asserted to stand under assets/goo/sounds. */
    private static List<String> filesOf(Identifier id) throws IOException {
        JsonArray variations = soundEntry(id).getAsJsonArray("sounds");
        List<String> files = new ArrayList<>();
        for (int index = 0; index < variations.size(); index++) {
            String name = variations.get(index).getAsJsonObject().get("name").getAsString();
            assertTrue(name.startsWith(GOO_NAMESPACE), id + " plays " + name + ", no sound the mod ships");
            String resource = "/assets/goo/sounds/" + name.substring(GOO_NAMESPACE.length()) + ".ogg";
            try (InputStream in = HoleSoundsTest.class.getResourceAsStream(resource)) {
                assertNotNull(in, resource + " is not on the classpath");
            }
            files.add(name);
        }
        return files;
    }

    private static void assertSubtitleNamesTheHole(Identifier id) throws IOException {
        JsonObject entry = soundEntry(id);
        assertTrue(entry.has("subtitle"), id + " carries no subtitle");
        String key = entry.get("subtitle").getAsString();
        JsonObject lang = readJson(EN_US_JSON);
        assertTrue(lang.has(key), "en_us.json names no " + key);
        String subtitle = lang.get(key).getAsString();
        assertTrue(subtitle.contains(HOLE), id + "'s subtitle reads \"" + subtitle + "\", naming no hole");
    }

    private static JsonObject soundEntry(Identifier id) throws IOException {
        JsonObject sounds = readJson(SOUNDS_JSON);
        assertTrue(sounds.has(id.getPath()), "sounds.json names no " + id.getPath());
        return sounds.getAsJsonObject(id.getPath());
    }

    private static JsonObject readJson(String resource) throws IOException {
        try (InputStream in = HoleSoundsTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, resource + " is not on the classpath");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
