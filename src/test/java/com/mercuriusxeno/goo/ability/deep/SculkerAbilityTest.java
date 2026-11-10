package com.mercuriusxeno.goo.ability.deep;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deep's brew ships as deep_sculker.json, named goo.ability.deep.sculker,
 * badged brew and running a sculker step, and deep's brew effect draws
 * Sculker's own icon in place of the missing-texture tile, all read off the
 * classpath (decision dive-is-renamed-sculker).
 */
class SculkerAbilityTest {

    private static final String SCULKER_JSON = "data/goo/goo_abilities/deep_sculker.json";
    private static final String SCULKER_ICON = "/assets/goo/textures/goo/ability/deep_sculker.png";
    private static final String DEEP_BREW_ICON = "/assets/goo/textures/mob_effect/deep_held.png";

    private static JsonObject sculker() throws IOException {
        try (InputStream in = SculkerAbilityTest.class.getClassLoader().getResourceAsStream(SCULKER_JSON)) {
            assertNotNull(in, SCULKER_JSON + " should ship");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    void shipsAsTheSculkerBrew() throws IOException {
        JsonObject sculker = sculker();
        assertEquals("goo.ability.deep.sculker", sculker.get("displayName").getAsString());
        assertEquals("durative", sculker.get("badge").getAsString());
    }

    /** Decision sculker-unlocks-with-the-echo-shard-recipe. */
    @Test
    void unlocksWithTheEchoShard() throws IOException {
        JsonArray requires = sculker().getAsJsonArray("requires");
        assertEquals(1, requires.size());
        assertEquals("minecraft:echo_shard", requires.get(0).getAsString());
    }

    @Test
    void deepBrewEffectDrawsSculkersIcon() throws IOException {
        assertArrayEquals(pixels(image(SCULKER_ICON)), pixels(image(DEEP_BREW_ICON)));
    }

    private static BufferedImage image(String path) throws IOException {
        try (InputStream in = SculkerAbilityTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path + " should ship");
            return ImageIO.read(in);
        }
    }

    private static int[] pixels(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }

    @Test
    void runsASculkerStep() throws IOException {
        boolean runsSculker = false;
        for (JsonElement step : sculker().getAsJsonArray("behaviors")) {
            runsSculker |= "sculker".equals(step.getAsJsonObject().get("type").getAsString());
        }
        assertTrue(runsSculker, "deep_sculker.json should run a sculker step");
    }
}
