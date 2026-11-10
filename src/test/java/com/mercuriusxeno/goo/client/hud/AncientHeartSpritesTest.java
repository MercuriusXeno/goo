package com.mercuriusxeno.goo.client.hud;

import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Covers that the HUD's ancient heart is the heart the Ancient icon draws in
 * front, whole and halved as the other HUD hearts are
 * (decision hud-draws-the-leftmost-heart-as-ancient).
 */
class AncientHeartSpritesTest {

    private static final String HEARTS = "/assets/goo/textures/gui/sprites/hud/heart/";
    private static final String ANCIENT_ICON = "/assets/goo/textures/goo/ability/yore_ancient.png";
    private static final int SPRITE_SIDE = 9;
    /** The 7x7 heart's texels inside the 9x9 sprite. */
    private static final int HEART_FIRST = 1;
    private static final int HEART_LAST = 7;
    /** The half heart's last column, its tip's. */
    private static final int HALF_LAST = 4;
    private static final int ALPHA_SHIFT = 24;
    /** The 7x7 heart every HUD heart fills, '#' for a texel inside it. */
    private static final String[] HEART = {
        ".##.##.",
        "#######",
        "#######",
        "#######",
        ".#####.",
        "..###..",
        "...#...",
    };
    /** Where the icon's front heart puts sprite texel (x, y): one column left and eight rows down. */
    private static final int ICON_SHIFT_X = -1;
    private static final int ICON_SHIFT_Y = 8;

    @Test
    void ancientFullIsTheIconsFrontHeart() throws IOException {
        BufferedImage full = read(HEARTS + "ancient_full.png");
        BufferedImage icon = read(ANCIENT_ICON);
        assertEquals(SPRITE_SIDE, full.getWidth());
        assertEquals(SPRITE_SIDE, full.getHeight());
        for (int y = 0; y < SPRITE_SIDE; y++) {
            for (int x = 0; x < SPRITE_SIDE; x++) {
                if (inHeart(x, y)) {
                    assertEquals(icon.getRGB(x + ICON_SHIFT_X, y + ICON_SHIFT_Y), full.getRGB(x, y),
                            "texel " + x + "," + y);
                } else {
                    assertEquals(0, full.getRGB(x, y) >>> ALPHA_SHIFT, "texel " + x + "," + y);
                }
            }
        }
    }

    private static boolean inHeart(int x, int y) {
        return y >= HEART_FIRST && y <= HEART_LAST && x >= HEART_FIRST && x <= HEART_LAST
                && HEART[y - HEART_FIRST].charAt(x - HEART_FIRST) == '#';
    }

    @Test
    void ancientHalfIsTheLeftHalfOfTheFullHeart() throws IOException {
        BufferedImage full = read(HEARTS + "ancient_full.png");
        BufferedImage half = read(HEARTS + "ancient_half.png");
        assertEquals(SPRITE_SIDE, half.getWidth());
        assertEquals(SPRITE_SIDE, half.getHeight());
        for (int y = 0; y < SPRITE_SIDE; y++) {
            for (int x = 0; x <= HALF_LAST; x++) {
                assertEquals(full.getRGB(x, y), half.getRGB(x, y), "texel " + x + "," + y);
            }
            for (int x = HALF_LAST + 1; x < SPRITE_SIDE; x++) {
                assertEquals(0, half.getRGB(x, y) >>> ALPHA_SHIFT, "texel " + x + "," + y);
            }
        }
    }

    private static BufferedImage read(String path) throws IOException {
        try (InputStream in = AncientHeartSpritesTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path + " is missing");
            return ImageIO.read(in);
        }
    }
}
