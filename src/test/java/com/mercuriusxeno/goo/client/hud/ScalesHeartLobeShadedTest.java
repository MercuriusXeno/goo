package com.mercuriusxeno.goo.client.hud;

import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers that the crystal heart's top-right lobe is lit from the top-left,
 * a highlight at its top-left and the darker mid tone down its right edge,
 * in the HUD sprite and in the Scales icon's front heart alike
 * (decision scales-heart-top-right-lobe-is-shaded).
 */
class ScalesHeartLobeShadedTest {

    private static final String CRYSTAL_FULL = "/assets/goo/textures/gui/sprites/hud/heart/crystal_full.png";
    private static final String SCALES_ICON = "/assets/goo/textures/goo/ability/crystal_scales.png";
    private static final Set<Integer> HIGHLIGHTS = Set.of(0xA8FAF2, 0xF0FFFF);
    private static final int SHADE = 0x26A8AA;
    private static final int RGB = 0xFFFFFF;
    /** The lobe's top-left texel in the 9x9 sprite, and its right column's rows. */
    private static final int SPRITE_LOBE_LEFT = 5;
    private static final int SPRITE_LOBE_RIGHT = 7;
    private static final int SPRITE_LOBE_TOP = 1;
    private static final int SPRITE_LOBE_BOTTOM = 4;
    /** Where the icon's front heart puts sprite texel (x, y): one column left and eight rows down. */
    private static final int ICON_SHIFT_X = -1;
    private static final int ICON_SHIFT_Y = 8;

    @Test
    void hudCrystalHeartLobeIsShaded() throws IOException {
        assertLobeShaded(read(CRYSTAL_FULL), 0, 0);
    }

    @Test
    void scalesIconCrystalHeartLobeIsShaded() throws IOException {
        assertLobeShaded(read(SCALES_ICON), ICON_SHIFT_X, ICON_SHIFT_Y);
    }

    private static void assertLobeShaded(BufferedImage image, int shiftX, int shiftY) {
        int topLeft = image.getRGB(SPRITE_LOBE_LEFT + shiftX, SPRITE_LOBE_TOP + shiftY) & RGB;
        assertTrue(HIGHLIGHTS.contains(topLeft), "lobe top-left is " + Integer.toHexString(topLeft));
        boolean shadedRightEdge = false;
        for (int y = SPRITE_LOBE_TOP; y <= SPRITE_LOBE_BOTTOM; y++) {
            shadedRightEdge |= (image.getRGB(SPRITE_LOBE_RIGHT + shiftX, y + shiftY) & RGB) == SHADE;
        }
        assertTrue(shadedRightEdge, "lobe right edge holds no 26a8aa");
    }

    private static BufferedImage read(String path) throws IOException {
        try (InputStream in = ScalesHeartLobeShadedTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path + " is missing");
            return ImageIO.read(in);
        }
    }
}
