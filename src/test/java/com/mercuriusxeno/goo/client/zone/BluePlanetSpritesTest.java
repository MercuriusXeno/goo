package com.mercuriusxeno.goo.client.zone;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.MoonPhase;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
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
 * ships, as basic as the moon's face: a square of the overworld's sea and land filling the sprite, lit on the side its
 * phase lights, its unlit part black, which the sky's additive draw shows as nothing.
 * decision planet-face-is-eight-hand-drawn-phase-sprites
 * decision overworld-phases-are-32x32-at-the-moons-lod-ratio
 * decision unlit-part-of-the-blue-planet-draws-nothing
 */
class BluePlanetSpritesTest {

    private static final String SPRITES = "/assets/goo/textures/environment/blue_planet/";
    private static final int TEXELS = 32;
    private static final int CHANNEL = 0xFF;
    private static final int ALPHA_SHIFT = 24;
    private static final int RED_SHIFT = 16;
    private static final int GREEN_SHIFT = 8;
    private static final int RGB = 0xFFFFFF;
    /** The outline the unlit part once drew round the planet, as vanilla keeps one round its new moon. */
    private static final int OUTLINE = 0x282E3A;

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

    /**
     * Each phase's sprite, by the name lunar's sky resolves for it, is 32 texels square.
     * decision overworld-phases-are-32x32-at-the-moons-lod-ratio
     *
     * @param planetPhase the planet's phase
     */
    @ParameterizedTest
    @EnumSource(MoonPhase.class)
    void eachPhaseIsAThirtyTwoTexelSquare(MoonPhase planetPhase) throws IOException {
        Identifier name = LunarSky.planetSprite(planetPhase);
        String path = "/assets/" + name.getNamespace() + "/" + name.getPath();
        try (InputStream in = BluePlanetSpritesTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path + " is missing");
            BufferedImage sprite = ImageIO.read(in);
            assertEquals(TEXELS, sprite.getWidth(), path);
            assertEquals(TEXELS, sprite.getHeight(), path);
        }
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

    /**
     * Each phase's unlit columns, counted from the side its phase darkens, and that side: the waxing phases dark on
     * the left, the waning on the right.
     * decision unlit-part-of-the-blue-planet-draws-nothing
     *
     * @param phase       the phase's sprite
     * @param unlitWidth  the columns its unlit part spans
     * @param darkOnLeft  whether the unlit part stands on the left
     */
    @ParameterizedTest
    @CsvSource({"full, 0, true", "waning_gibbous, 8, false", "third_quarter, 16, false", "waning_crescent, 24, false",
        "new, 32, true", "waxing_crescent, 24, true", "first_quarter, 16, true", "waxing_gibbous, 8, true"})
    void theUnlitPartDrawsBlackAndTheLitPartKeepsItsFace(String phase, int unlitWidth, boolean darkOnLeft)
            throws IOException {
        BufferedImage sprite = read(phase);
        for (int y = 0; y < TEXELS; y++) {
            for (int x = 0; x < TEXELS; x++) {
                int argb = sprite.getRGB(x, y);
                boolean unlit = darkOnLeft ? x < unlitWidth : x >= TEXELS - unlitWidth;
                String at = String.format("%s (%d, %d) holds #%06x", phase, x, y, argb & RGB);
                assertTrue(unlit ? brightness(argb) == 0 : brightness(argb) > 0, at);
                assertTrue((argb & RGB) != OUTLINE, at + ", the outline vanilla keeps round its new moon");
            }
        }
    }
}
