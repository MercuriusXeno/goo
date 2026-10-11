package com.mercuriusxeno.goo.client.radial;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Covers that each crystal ability shows a drawn icon rather than the goo
 * droplet placeholder, read by its silhouette, and that the crystal brew
 * effect has an icon.
 */
class CrystalAbilityIconsTest {

    private static final String ABILITY_ICONS = "/assets/goo/textures/goo/ability/";
    private static final String CRYSTAL_BREW = "/assets/goo/textures/mob_effect/crystal_brew.png";
    private static final int SIDE = 16;
    private static final int ALPHA_SHIFT = 24;

    /** The goo droplet placeholder's silhouette, '#' for an opaque pixel. */
    private static final String PLACEHOLDER = String.join("\n",
            "....########....",
            "...##########...",
            "...##########...",
            ".##############.",
            "################",
            "################",
            "################",
            "################",
            "################",
            ".##############.",
            "...##########...",
            "...##########...",
            "....########....",
            "......####......",
            "......####......",
            ".......##.......");

    @ParameterizedTest
    @ValueSource(strings = {"crystal_cloud", "crystal_scales", "crystal_shards_tap", "crystal_glitter"})
    void crystalIconIsDrawnRatherThanThePlaceholder(String ability) throws IOException {
        BufferedImage icon = read(ABILITY_ICONS + ability + ".png");
        assertNotEquals(PLACEHOLDER, silhouette(icon), ability + " still shows the goo droplet placeholder");
    }

    @Test
    void crystalBrewEffectHasASixteenPixelIcon() throws IOException {
        BufferedImage icon = read(CRYSTAL_BREW);
        assertEquals(SIDE, icon.getWidth());
        assertEquals(SIDE, icon.getHeight());
    }

    private static BufferedImage read(String path) throws IOException {
        try (InputStream in = CrystalAbilityIconsTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path + " is missing");
            return ImageIO.read(in);
        }
    }

    private static String silhouette(BufferedImage icon) {
        StringBuilder rows = new StringBuilder();
        for (int y = 0; y < icon.getHeight(); y++) {
            if (y > 0) {
                rows.append('\n');
            }
            for (int x = 0; x < icon.getWidth(); x++) {
                rows.append((icon.getRGB(x, y) >>> ALPHA_SHIFT) > 0 ? '#' : '.');
            }
        }
        return rows.toString();
    }
}
