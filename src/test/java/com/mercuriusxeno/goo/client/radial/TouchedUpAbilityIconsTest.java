package com.mercuriusxeno.goo.client.radial;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    private static final int RGB = 0xFFFFFF;

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

    @Nested
    class DrainIcon {

        // drain-icon-is-a-heart-with-a-dot-core
        private static final Set<Integer> FORMER_PALETTE =
                Set.of(0x7a1428, 0xc0283c, 0xc65197, 0x090a14, 0x402751, 0x1e1d39);
        private static final Set<Integer> DARK_CORE = Set.of(0x090a14, 0x402751, 0x1e1d39);
        private static final int CENTER_LOW = 5;
        private static final int CENTER_HIGH = 10;
        private static final int LEAST_CORE_TEXELS = 8;
        private static final int WIDEST_POINT = 2;

        @Test
        void topRowHoldsTwoLobes() throws IOException {
            BufferedImage icon = read("hex_drain");
            assertEquals(2, opaqueRuns(icon, firstOpaqueRow(icon)).size(), "the heart's top row should be two lobes");
        }

        @Test
        void bottomRowIsAPoint() throws IOException {
            BufferedImage icon = read("hex_drain");
            List<int[]> runs = opaqueRuns(icon, lastOpaqueRow(icon));
            assertEquals(1, runs.size(), "the heart's bottom row should be one point");
            assertTrue(runs.get(0)[1] - runs.get(0)[0] + 1 <= WIDEST_POINT, "the heart's point is too wide");
        }

        @Test
        void drawsInTheFormerPalette() throws IOException {
            BufferedImage icon = read("hex_drain");
            for (int rgb : opaqueColors(icon)) {
                assertTrue(FORMER_PALETTE.contains(rgb), String.format("#%06x is not Drain's former coloration", rgb));
            }
        }

        @Test
        void darkCoreSitsAtTheCenter() throws IOException {
            BufferedImage icon = read("hex_drain");
            int coreTexels = 0;
            for (int y = 0; y < icon.getHeight(); y++) {
                for (int x = 0; x < icon.getWidth(); x++) {
                    if (DARK_CORE.contains(icon.getRGB(x, y) & RGB) && isOpaque(icon.getRGB(x, y))) {
                        assertTrue(inCenter(x) && inCenter(y), "a dark core texel sits off center at " + x + "," + y);
                        coreTexels++;
                    }
                }
            }
            assertTrue(coreTexels >= LEAST_CORE_TEXELS, "the dark core holds only " + coreTexels + " texels");
        }

        private boolean inCenter(int coordinate) {
            return coordinate >= CENTER_LOW && coordinate <= CENTER_HIGH;
        }
    }

    @Nested
    class GluttonyIcon {

        // gluttony-icon-redrawn-at-native-texels
        private static final int BLOCK = 2;

        /** A sprite scaled up from any offset fills the 2x2 grid of one phase with one-color blocks. */
        @ParameterizedTest
        @ValueSource(ints = {0, 1, 2, 3})
        void isNotATwiceScaledSprite(int phase) throws IOException {
            BufferedImage icon = read("jelly_gluttony");
            int drawnBlocks = 0;
            int uniformBlocks = 0;
            for (int y = phase / BLOCK; y + 1 < icon.getHeight(); y += BLOCK) {
                for (int x = phase % BLOCK; x + 1 < icon.getWidth(); x += BLOCK) {
                    if (isDrawn(icon, x, y)) {
                        drawnBlocks++;
                        uniformBlocks += isUniform(icon, x, y) ? 1 : 0;
                    }
                }
            }
            assertTrue(uniformBlocks * 2 < drawnBlocks, uniformBlocks + " of " + drawnBlocks
                    + " drawn 2x2 blocks at phase " + phase + " are one color, a scaled-up sprite");
        }

        private boolean isDrawn(BufferedImage icon, int x, int y) {
            return isOpaque(icon.getRGB(x, y)) || isOpaque(icon.getRGB(x + 1, y))
                    || isOpaque(icon.getRGB(x, y + 1)) || isOpaque(icon.getRGB(x + 1, y + 1));
        }

        private boolean isUniform(BufferedImage icon, int x, int y) {
            int corner = icon.getRGB(x, y);
            return icon.getRGB(x + 1, y) == corner && icon.getRGB(x, y + 1) == corner
                    && icon.getRGB(x + 1, y + 1) == corner;
        }
    }

    private static Set<Integer> opaqueColors(BufferedImage icon) {
        Set<Integer> colors = new HashSet<>();
        for (int y = 0; y < icon.getHeight(); y++) {
            for (int x = 0; x < icon.getWidth(); x++) {
                if (isOpaque(icon.getRGB(x, y))) {
                    colors.add(icon.getRGB(x, y) & RGB);
                }
            }
        }
        return colors;
    }

    private static int firstOpaqueRow(BufferedImage icon) {
        for (int y = 0; y < icon.getHeight(); y++) {
            if (!opaqueRuns(icon, y).isEmpty()) {
                return y;
            }
        }
        throw new AssertionError("the icon is empty");
    }

    private static int lastOpaqueRow(BufferedImage icon) {
        for (int y = icon.getHeight() - 1; y >= 0; y--) {
            if (!opaqueRuns(icon, y).isEmpty()) {
                return y;
            }
        }
        throw new AssertionError("the icon is empty");
    }

    /** Each horizontal run of opaque texels in row y, as its first and last column. */
    private static List<int[]> opaqueRuns(BufferedImage icon, int y) {
        List<int[]> runs = new ArrayList<>();
        int start = -1;
        for (int x = 0; x <= icon.getWidth(); x++) {
            boolean opaque = x < icon.getWidth() && isOpaque(icon.getRGB(x, y));
            if (opaque && start < 0) {
                start = x;
            } else if (!opaque && start >= 0) {
                runs.add(new int[] {start, x - 1});
                start = -1;
            }
        }
        return runs;
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
