package com.mercuriusxeno.goo.tools;

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
 * Tests that every saturated pixel of jelly's committed fluid and blob sprites draws in a hue
 * jelly's icon draws, crimson through coral to amber, read through the classpath.
 */
class JellySpritesMatchIconTest {

    private static final String TEXTURES = "/assets/goo/textures/";
    private static final String ICON = "goo/type/jelly.png";
    private static final float HUE_SLACK = 15f / 360f;
    private static final float SATURATION_FLOOR = 0.15f;

    // jelly-fluid-and-blobs-move-to-the-icons-colors
    @ParameterizedTest
    @ValueSource(strings = {"fluid/jelly_fluid.png", "item/jelly_goo_tiny.png", "item/jelly_goo_small.png",
            "item/jelly_goo_base.png", "item/jelly_goo_large.png"})
    void spriteDrawsInTheIconsHues(String sprite) throws IOException {
        float[] span = iconHueSpan(readImage(ICON));
        BufferedImage strip = readImage(sprite);
        int outside = 0;
        String first = null;
        for (int y = 0; y < strip.getHeight(); y++) {
            for (int x = 0; x < strip.getWidth(); x++) {
                int argb = strip.getRGB(x, y);
                float[] hsb = hsb(argb);
                if (isOpaque(argb) && hsb[1] > SATURATION_FLOOR
                        && !withinSpan(centeredHue(hsb[0]), span[0] - HUE_SLACK, span[1] + HUE_SLACK)) {
                    outside++;
                    first = first == null ? x + "," + y + " #" + Integer.toHexString(argb) : first;
                }
            }
        }
        assertTrue(outside == 0, sprite + " holds " + outside + " saturated pixels outside the icon's hues "
                + (span[0] * 360) + ".." + (span[1] * 360) + " degrees, the first at " + first);
    }

    private static float[] iconHueSpan(BufferedImage icon) {
        float low = Float.MAX_VALUE;
        float high = -Float.MAX_VALUE;
        for (int y = 0; y < icon.getHeight(); y++) {
            for (int x = 0; x < icon.getWidth(); x++) {
                int argb = icon.getRGB(x, y);
                if (isOpaque(argb)) {
                    float hue = centeredHue(hsb(argb)[0]);
                    low = Math.min(low, hue);
                    high = Math.max(high, hue);
                }
            }
        }
        return new float[]{low, high};
    }

    /**
     * Moves the hue wheel's seam opposite red, so the icon's crimson reads as a small negative
     * hue beside its coral and amber rather than as a hue near a full turn.
     */
    private static float centeredHue(float hue) {
        return hue > 0.5f ? hue - 1f : hue;
    }

    private static boolean withinSpan(float hue, float low, float high) {
        return hue >= low && hue <= high;
    }

    private static boolean isOpaque(int argb) {
        return argb >>> 24 != 0;
    }

    private static float[] hsb(int argb) {
        return Color.RGBtoHSB((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, null);
    }

    private static BufferedImage readImage(String path) throws IOException {
        try (InputStream in = JellySpritesMatchIconTest.class.getResourceAsStream(TEXTURES + path)) {
            assertNotNull(in, "missing on the classpath: " + TEXTURES + path);
            return ImageIO.read(in);
        }
    }
}
