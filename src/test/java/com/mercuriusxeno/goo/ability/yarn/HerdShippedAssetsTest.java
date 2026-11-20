package com.mercuriusxeno.goo.ability.yarn;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Covers what Herd ships beside its JSON: its 16x16 icon, the Yarn held
 * effect drawing that icon, and its name and description in the lang.
 * leader-is-zoos-brew-ability
 */
class HerdShippedAssetsTest {

    private static final String HERD_ICON = "/assets/goo/textures/goo/ability/yarn_herd.png";
    private static final String YARN_HELD_ICON = "/assets/goo/textures/mob_effect/yarn_held.png";
    private static final String EN_US = "/assets/goo/lang/en_us.json";
    private static final String HERD_NAME = "goo.ability.yarn.herd";
    private static final String HERD_DESCRIPTION = HERD_NAME + ".description";
    private static final int SIDE = 16;

    @Test
    void yarnHeldEffectDrawsHerdIcon() throws IOException {
        BufferedImage held = read(YARN_HELD_ICON);
        BufferedImage herd = read(HERD_ICON);

        assertEquals(SIDE, herd.getWidth());
        assertEquals(SIDE, herd.getHeight());
        assertArrayEquals(pixels(herd), pixels(held));
    }

    @Test
    void langNamesHerdAndDescribesIt() throws IOException {
        JsonObject lang;
        try (InputStream in = HerdShippedAssetsTest.class.getResourceAsStream(EN_US)) {
            assertNotNull(in, EN_US + " is missing");
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                lang = JsonParser.parseReader(reader).getAsJsonObject();
            }
        }

        assertEquals("Herd", lang.has(HERD_NAME) ? lang.get(HERD_NAME).getAsString() : null);
        assertFalse(!lang.has(HERD_DESCRIPTION) || lang.get(HERD_DESCRIPTION).getAsString().isBlank(),
                HERD_DESCRIPTION + " holds no description");
    }

    private static BufferedImage read(String path) throws IOException {
        try (InputStream in = HerdShippedAssetsTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path + " is missing");
            return ImageIO.read(in);
        }
    }

    private static int[] pixels(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }
}
