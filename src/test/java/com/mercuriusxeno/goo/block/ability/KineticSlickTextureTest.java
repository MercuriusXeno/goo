package com.mercuriusxeno.goo.block.ability;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The slick film's texture, read through the classpath as the texels it ships, wears Kinetic's
 * electric blue and white: each opaque texel sits on the hue of Kinetic's wheel channel or reads
 * near white, and both kinds show.
 * slick-coats-the-ground-with-kinetic-speed
 * kinetic-is-electric-blue-and-white
 */
class KineticSlickTextureTest {

    private static final String FILM = "/assets/goo/textures/block/kinetic_slick.png";
    private static final String KINETIC_TYPE = "/data/goo/goo/goo_type/kinetic.json";
    /** How far, in degrees, a blue texel's hue may sit from the wheel's. */
    private static final float HUE_TOLERANCE = 15f;
    /** A near-white texel's saturation ceiling. */
    private static final float WHITE_SATURATION = 0.2f;
    /** A near-white texel's brightness floor. */
    private static final float WHITE_VALUE = 0.9f;
    private static final int ALPHA_SHIFT = 24;
    private static final int CHANNEL = 0xFF;

    @Test
    void everyOpaqueTexelIsKineticBlueOrNearWhiteAndBothShow() throws IOException {
        float wheelHue = hueOf(Integer.parseInt(wheelChannel(), 16));
        BufferedImage film = read();
        boolean blue = false;
        boolean white = false;
        for (int y = 0; y < film.getHeight(); y++) {
            for (int x = 0; x < film.getWidth(); x++) {
                int argb = film.getRGB(x, y);
                if (argb >>> ALPHA_SHIFT == 0) {
                    continue;
                }
                float[] hsb = hsb(argb);
                boolean nearWhite = hsb[1] <= WHITE_SATURATION && hsb[2] >= WHITE_VALUE;
                boolean onTheWheel = hueGap(hsb[0] * 360f, wheelHue) <= HUE_TOLERANCE;
                assertTrue(nearWhite || onTheWheel,
                        String.format("texel (%d,%d) #%06x is neither Kinetic blue nor near white", x, y, argb & 0xFFFFFF));
                white |= nearWhite;
                blue |= onTheWheel && !nearWhite;
            }
        }
        assertTrue(blue, "the film shows no Kinetic blue texel");
        assertTrue(white, "the film shows no near-white texel");
    }

    private static float hueGap(float first, float second) {
        float gap = Math.abs(first - second);
        return Math.min(gap, 360f - gap);
    }

    private static float hueOf(int rgb) {
        return hsb(rgb)[0] * 360f;
    }

    private static float[] hsb(int rgb) {
        return Color.RGBtoHSB(rgb >> 16 & CHANNEL, rgb >> 8 & CHANNEL, rgb & CHANNEL, null);
    }

    private static String wheelChannel() throws IOException {
        try (InputStream in = KineticSlickTextureTest.class.getResourceAsStream(KINETIC_TYPE)) {
            assertNotNull(in, KINETIC_TYPE + " is missing");
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject().get("wheel").getAsString();
            }
        }
    }

    private static BufferedImage read() throws IOException {
        try (InputStream in = KineticSlickTextureTest.class.getResourceAsStream(FILM)) {
            assertNotNull(in, FILM + " is missing");
            return ImageIO.read(in);
        }
    }
}
