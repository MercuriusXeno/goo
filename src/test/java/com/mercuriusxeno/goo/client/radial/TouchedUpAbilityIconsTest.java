package com.mercuriusxeno.goo.client.radial;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the ability icons the operator asked touched up, each read through
 * the classpath as the 16x16 texels it ships.
 */
class TouchedUpAbilityIconsTest {

    private static final String ABILITY_ICONS = "/assets/goo/textures/goo/ability/";
    private static final int ALPHA_SHIFT = 24;
    private static final int CHANNEL = 0xFF;

    @Nested
    class GlowIcons {

        // glow-icons-take-a-darker-yellow-edge
        private static final float LEAST_YELLOW_HUE = 35f / 360f;
        private static final float MOST_YELLOW_HUE = 60f / 360f;
        private static final float SATURATION_FLOOR = 0.2f;
        private static final float VALUE_FLOOR = 0.5f;

        @ParameterizedTest
        @ValueSource(strings = {"glow_lux", "glow_sunbeam", "glow_radiant", "glow_crystal"})
        void everyOpaqueTexelIsYellow(String ability) throws IOException {
            BufferedImage icon = read(ability);
            for (int y = 0; y < icon.getHeight(); y++) {
                for (int x = 0; x < icon.getWidth(); x++) {
                    int argb = icon.getRGB(x, y);
                    if (isOpaque(argb)) {
                        assertYellow(ability, x, y, argb);
                    }
                }
            }
        }

        private void assertYellow(String ability, int x, int y, int argb) {
            float[] hsv = Color.RGBtoHSB(argb >> 16 & CHANNEL, argb >> 8 & CHANNEL, argb & CHANNEL, null);
            String texel = String.format("%s (%d,%d) #%06x", ability, x, y, argb & 0xFFFFFF);
            assertTrue(hsv[0] >= LEAST_YELLOW_HUE && hsv[0] <= MOST_YELLOW_HUE, texel + " is not a yellow hue");
            assertTrue(hsv[1] >= SATURATION_FLOOR, texel + " is desaturated");
            assertTrue(hsv[2] >= VALUE_FLOOR, texel + " is near-black");
        }
    }

    private static boolean isOpaque(int argb) {
        return (argb >>> ALPHA_SHIFT) > 0;
    }

    private static BufferedImage read(String ability) throws IOException {
        String path = ABILITY_ICONS + ability + ".png";
        try (InputStream in = TouchedUpAbilityIconsTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path + " is missing");
            return ImageIO.read(in);
        }
    }
}
