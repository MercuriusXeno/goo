package com.mercuriusxeno.goo.ability.zoo;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Covers what Kin ships beside its JSON: its 16x16 icon, the Zoo held
 * effect drawing that icon, and its name and description in the lang.
 * leader-is-zoos-brew-ability
 */
class KinShippedAssetsTest {

    private static final String KIN_ICON = "/assets/goo/textures/goo/ability/zoo_kin.png";
    private static final String ZOO_BREW_ICON = "/assets/goo/textures/mob_effect/zoo_held.png";
    private static final String EN_US = "/assets/goo/lang/en_us.json";
    private static final String KIN_NAME = "goo.ability.zoo.kin";
    private static final String KIN_DESCRIPTION = KIN_NAME + ".description";
    private static final int SIDE = 16;

    @Test
    void zooBrewEffectDrawsKinIcon() throws IOException {
        BufferedImage brew = read(ZOO_BREW_ICON);
        BufferedImage kin = read(KIN_ICON);

        assertEquals(SIDE, kin.getWidth());
        assertEquals(SIDE, kin.getHeight());
        assertArrayEquals(pixels(kin), pixels(brew));
    }

    @Test
    void langNamesKinAndDescribesIt() throws IOException {
        JsonObject lang;
        try (InputStream in = KinShippedAssetsTest.class.getResourceAsStream(EN_US)) {
            assertNotNull(in, EN_US + " is missing");
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                lang = JsonParser.parseReader(reader).getAsJsonObject();
            }
        }

        assertEquals("Kin", lang.has(KIN_NAME) ? lang.get(KIN_NAME).getAsString() : null);
        assertFalse(!lang.has(KIN_DESCRIPTION) || lang.get(KIN_DESCRIPTION).getAsString().isBlank(),
                KIN_DESCRIPTION + " holds no description");
    }

    private static BufferedImage read(String path) throws IOException {
        try (InputStream in = KinShippedAssetsTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path + " is missing");
            return ImageIO.read(in);
        }
    }

    private static int[] pixels(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }
}
