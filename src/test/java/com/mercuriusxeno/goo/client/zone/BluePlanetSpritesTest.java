package com.mercuriusxeno.goo.client.zone;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the blue planet's eight hand-drawn phase sprites, each read through the classpath as the 32x32 texels it
 * ships: a square of the overworld's sea and land filling the sprite as vanilla's moon is a square, lit on the side its
 * phase lights, its unlit part still drawn, darkened.
 * decision planet-face-is-eight-hand-drawn-phase-sprites
 */
class BluePlanetSpritesTest {

    private static final String SPRITES = "/assets/goo/textures/environment/blue_planet/";
    private static final int TEXELS = 32;
    private static final int CHANNEL = 0xFF;
    private static final int ALPHA_SHIFT = 24;
    private static final int RED_SHIFT = 16;
    private static final int GREEN_SHIFT = 8;

    private static BufferedImage read(String phase) throws IOException {
        String path = SPRITES + phase + ".png";
        try (InputStream in = BluePlanetSpritesTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path + " is missing");
            return ImageIO.read(in);
        }
    }

    private static int red(int argb) {
        return argb >> RED_SHIFT & CHANNEL;
    }

    private static int green(int argb) {
        return argb >> GREEN_SHIFT & CHANNEL;
    }

    private static int blue(int argb) {
        return argb & CHANNEL;
    }

    private static int brightness(int argb) {
        return red(argb) + green(argb) + blue(argb);
    }

    /** The opaque, non-black texels of one half of a sprite: the disc's texels there. */
    private static List<Integer> discTexels(BufferedImage sprite, boolean leftHalf) {
        List<Integer> texels = new ArrayList<>();
        for (int y = 0; y < sprite.getHeight(); y++) {
            for (int x = leftHalf ? 0 : TEXELS / 2; x < (leftHalf ? TEXELS / 2 : TEXELS); x++) {
                int argb = sprite.getRGB(x, y);
                if ((argb >>> ALPHA_SHIFT) > 0 && brightness(argb) > 0) {
                    texels.add(argb);
                }
            }
        }
        return texels;
    }

    private static double meanBrightness(List<Integer> texels) {
        return texels.stream().mapToInt(BluePlanetSpritesTest::brightness).average().orElse(0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"full", "waning_gibbous", "third_quarter", "waning_crescent", "new", "waxing_crescent",
        "first_quarter", "waxing_gibbous"})
    void eachPhaseIsAThirtyTwoTexelSquare(String phase) throws IOException {
        BufferedImage sprite = read(phase);
        assertEquals(TEXELS, sprite.getWidth(), phase);
        assertEquals(TEXELS, sprite.getHeight(), phase);
    }

    @Test
    void theFullPlanetIsASquareFillingItsSprite() throws IOException {
        BufferedImage full = read("full");
        assertEquals(TEXELS * TEXELS, discTexels(full, true).size() + discTexels(full, false).size());
    }

    @Test
    void theFullPlanetHoldsBlueSeaAndGreenLand() throws IOException {
        BufferedImage full = read("full");
        List<Integer> disc = new ArrayList<>(discTexels(full, true));
        disc.addAll(discTexels(full, false));
        assertTrue(disc.stream().anyMatch(argb -> blue(argb) > red(argb) && blue(argb) > green(argb)),
                "the full planet holds no blue sea");
        assertTrue(disc.stream().anyMatch(argb -> green(argb) > red(argb) && green(argb) > blue(argb)),
                "the full planet holds no green land");
    }

    @Test
    void theWaningHalfIsLitOnItsLeft() throws IOException {
        BufferedImage waning = read("third_quarter");
        assertTrue(meanBrightness(discTexels(waning, true)) > meanBrightness(discTexels(waning, false)));
    }

    @Test
    void theWaxingHalfIsLitOnItsRight() throws IOException {
        BufferedImage waxing = read("first_quarter");
        assertTrue(meanBrightness(discTexels(waxing, false)) > meanBrightness(discTexels(waxing, true)));
    }

    @Test
    void theUnlitHalfIsDrawnDarkerThanTheLitHalf() throws IOException {
        BufferedImage waning = read("third_quarter");
        List<Integer> unlit = discTexels(waning, false);
        List<Integer> lit = discTexels(waning, true);
        assertTrue(unlit.size() > TEXELS, "the unlit half draws " + unlit.size() + " texels of the disc");
        int brightestUnlit = unlit.stream().mapToInt(BluePlanetSpritesTest::brightness).max().orElse(0);
        assertTrue(brightestUnlit < meanBrightness(lit),
                "the unlit half's brightest texel " + brightestUnlit + " should sit under the lit half's mean");
    }
}
