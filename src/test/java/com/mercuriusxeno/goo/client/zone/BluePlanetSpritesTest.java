package com.mercuriusxeno.goo.client.zone;

import com.mercuriusxeno.goo.client.zone.PlanetPhaseTexels.MapTexel;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.MoonPhase;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the blue planet's eight hand-drawn phase sprites, each read through the classpath as the 32x32 texels it
 * ships, as basic as the moon's face: a square of the overworld's sea and land filling the sprite, lit on the side its
 * phase lights, its unlit part black, which the sky's additive draw shows as nothing.
 * decision planet-face-is-eight-hand-drawn-phase-sprites
 * decision overworld-phases-are-32x32-at-the-moons-lod-ratio
 * decision unlit-part-of-the-blue-planet-draws-nothing
 * decision overworld-ice-moves-inland-off-the-edge
 * decision overworld-rim-darkens-like-the-moons
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

    private static MapTexel mapTexel(BufferedImage sprite, String phase, int x, int y) {
        int rgb = sprite.getRGB(x, y) & RGB;
        return PlanetPhaseTexels.readBack(rgb).orElseThrow(() -> new AssertionError(String.format(
                "%s (%d, %d) holds #%06x, no map color scaled", phase, x, y, rgb)));
    }

    /**
     * decision overworld-ice-moves-inland-off-the-edge
     */
    @Nested
    class InlandIce {

        @ParameterizedTest
        @ValueSource(strings = {"full", "waning_gibbous", "third_quarter", "waning_crescent", "new", "waxing_crescent",
            "first_quarter", "waxing_gibbous"})
        void noIceOrMountainLiesOnAnyEdge(String phase) throws IOException {
            BufferedImage sprite = read(phase);
            for (int along = 0; along < TEXELS; along++) {
                int[][] edgeTexels = {{along, 0}, {along, TEXELS - 1}, {0, along}, {TEXELS - 1, along}};
                for (int[] at : edgeTexels) {
                    if (brightness(sprite.getRGB(at[0], at[1])) == 0) {
                        continue;
                    }
                    int color = mapTexel(sprite, phase, at[0], at[1]).color();
                    assertFalse(color == PlanetPhaseTexels.ICE || color == PlanetPhaseTexels.MOUNTAIN,
                            String.format("%s (%d, %d) holds ice or mountain on the edge", phase, at[0], at[1]));
                }
            }
        }

        @Test
        void theFullPlanetStillHoldsIce() throws IOException {
            BufferedImage full = read("full");
            boolean ice = false;
            for (int y = 0; y < TEXELS && !ice; y++) {
                for (int x = 0; x < TEXELS && !ice; x++) {
                    ice = mapTexel(full, "full", x, y).color() == PlanetPhaseTexels.ICE;
                }
            }
            assertTrue(ice, "the full planet holds no ice");
        }
    }

    /**
     * decision overworld-rim-darkens-like-the-moons
     */
    @Nested
    class Rim {
        private static final double FACTOR_TOLERANCE = 0.02;
        private static final double EDGE_LOW = 0.4;
        private static final double EDGE_HIGH = 0.6;

        /**
         * Every lit texel reads back as a map color scaled by the rim: about half on a lit edge's outermost line,
         * unscaled eight texels in, never rising toward the edge, and the terminator fade's texels by the smaller of
         * the fade and the rim.
         *
         * @param name       the phase's sprite
         * @param unlitWidth the columns its unlit part spans
         * @param darkOnLeft whether the unlit part stands on the left
         */
        @ParameterizedTest
        @CsvSource({"full, 0, true", "waning_gibbous, 8, false", "third_quarter, 16, false",
            "waning_crescent, 24, false", "waxing_crescent, 24, true", "first_quarter, 16, true",
            "waxing_gibbous, 8, true"})
        void eachLitEdgeDarkensTowardTheRim(String name, int unlitWidth, boolean darkOnLeft) throws IOException {
            PlanetPhaseTexels phase = new PlanetPhaseTexels(name, unlitWidth, darkOnLeft);
            BufferedImage sprite = read(name);
            double[][] factors = new double[TEXELS][TEXELS];
            for (int y = 0; y < TEXELS; y++) {
                for (int x = 0; x < TEXELS; x++) {
                    if (!phase.unlit(x)) {
                        factors[x][y] = mapTexel(sprite, name, x, y).factor();
                        assertFactorAt(phase, x, y, factors[x][y]);
                    }
                }
            }
            for (int along = 0; along < TEXELS; along++) {
                assertNeverRisesTowardTheEdge(phase, factors, along, 0, 0, 1);
                assertNeverRisesTowardTheEdge(phase, factors, along, TEXELS - 1, 0, -1);
                if (phase.litOnLeft()) {
                    assertNeverRisesTowardTheEdge(phase, factors, 0, along, 1, 0);
                }
                if (phase.litOnRight()) {
                    assertNeverRisesTowardTheEdge(phase, factors, TEXELS - 1, along, -1, 0);
                }
            }
        }

        private void assertFactorAt(PlanetPhaseTexels phase, int x, int y, double factor) {
            String at = String.format("%s (%d, %d) scaled by %.3f", phase.name(), x, y, factor);
            double rim = phase.rimFactor(x, y);
            if (phase.inFade(x)) {
                assertEquals(Math.min(phase.fadeFactor(x), rim), factor, FACTOR_TOLERANCE, at + " in the fade");
                return;
            }
            if (rim == 1.0) {
                assertEquals(1.0, factor, FACTOR_TOLERANCE, at + " inside the rim");
            }
            boolean onLitEdgeLine = y == 0 || y == TEXELS - 1 || phase.litOnLeft() && x == 0
                    || phase.litOnRight() && x == TEXELS - 1;
            if (onLitEdgeLine) {
                assertTrue(factor >= EDGE_LOW && factor <= EDGE_HIGH, at + " on a lit edge");
            }
        }

        private void assertNeverRisesTowardTheEdge(PlanetPhaseTexels phase, double[][] factors, int edgeX, int edgeY,
                int stepX, int stepY) {
            for (int in = 0; in < PlanetPhaseTexels.RIM_TEXELS; in++) {
                int x = edgeX + stepX * in;
                int y = edgeY + stepY * in;
                int nextX = x + stepX;
                int nextY = y + stepY;
                if (phase.unlit(x) || phase.inFade(x) || phase.unlit(nextX) || phase.inFade(nextX)) {
                    continue;
                }
                assertTrue(factors[x][y] <= factors[nextX][nextY] + FACTOR_TOLERANCE, String.format(
                        "%s rises toward the edge from (%d, %d) at %.3f to (%d, %d) at %.3f", phase.name(), nextX,
                        nextY, factors[nextX][nextY], x, y, factors[x][y]));
            }
        }
    }
}
