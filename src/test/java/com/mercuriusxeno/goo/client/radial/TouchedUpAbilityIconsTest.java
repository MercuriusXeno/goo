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
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        private static final Set<Integer> DARK_HEART = Set.of(0x090a14, 0x402751, 0x1e1d39);
        private static final int CENTER_LEFT = 5;
        private static final int CENTER_RIGHT = 11;
        private static final int CENTER_TOP = 4;
        private static final int CENTER_BOTTOM = 10;
        private static final int WIDEST_POINT = 2;

        @Test
        void heartSitsInTheCenterSevenBySeven() throws IOException {
            BufferedImage heart = darkHeart(read("hex_drain"));
            for (int y = 0; y < heart.getHeight(); y++) {
                for (int x = 0; x < heart.getWidth(); x++) {
                    boolean inCenter = x >= CENTER_LEFT && x <= CENTER_RIGHT && y >= CENTER_TOP && y <= CENTER_BOTTOM;
                    assertTrue(inCenter || !isOpaque(heart.getRGB(x, y)), "a heart texel sits off center at " + x + "," + y);
                }
            }
        }

        @Test
        void heartTopRowHoldsTwoLobes() throws IOException {
            BufferedImage heart = darkHeart(read("hex_drain"));
            assertEquals(2, opaqueRuns(heart, firstOpaqueRow(heart)).size(), "the heart's top row should be two lobes");
        }

        @Test
        void heartBottomRowIsAPoint() throws IOException {
            BufferedImage heart = darkHeart(read("hex_drain"));
            List<int[]> runs = opaqueRuns(heart, lastOpaqueRow(heart));
            assertEquals(1, runs.size(), "the heart's bottom row should be one point");
            assertTrue(runs.get(0)[1] - runs.get(0)[0] + 1 <= WIDEST_POINT, "the heart's point is too wide");
        }

        /** The icon with every texel cleared but the heart's dark colors, so the rays drop out. */
        private BufferedImage darkHeart(BufferedImage icon) {
            BufferedImage heart = new BufferedImage(icon.getWidth(), icon.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < icon.getHeight(); y++) {
                for (int x = 0; x < icon.getWidth(); x++) {
                    int argb = icon.getRGB(x, y);
                    if (isOpaque(argb) && DARK_HEART.contains(argb & RGB)) {
                        heart.setRGB(x, y, argb);
                    }
                }
            }
            return heart;
        }

        @Test
        void drawsInTheFormerPalette() throws IOException {
            BufferedImage icon = read("hex_drain");
            for (int rgb : opaqueColors(icon)) {
                assertTrue(FORMER_PALETTE.contains(rgb), String.format("#%06x is not Drain's former coloration", rgb));
            }
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

    @Nested
    class UpdraftIcon {

        // updraft-icon-is-a-floating-slime-with-up-arrows
        /** The floating slime typhoon_shulk.png drew before shulk-icon-is-a-shulker, '.' for a clear texel. */
        private static final String[] FORMER_SHULK_SLIME = {
            ".aaaaaa.",
            "abbcccda",
            "abecceda",
            "acecdeda",
            "accdddfa",
            "acddeffa",
            "adddfffa",
            ".aaaaaa.",
        };
        private static final Map<Character, Integer> SLIME_COLORS = Map.of(
                'a', 0x16231a, 'b', 0xa2cf96, 'c', 0x7fb077, 'd', 0x5f8f5c, 'e', 0x14211a, 'f', 0x456b47);
        private static final Set<Integer> PRISM_COLORS = Set.of(0xf7fffc, 0xdcf7ee, 0xb5e3d6);

        @Test
        void carriesShulksFormerFloatingSlime() throws IOException {
            BufferedImage icon = read("typhoon_updraft");
            int side = FORMER_SHULK_SLIME.length;
            boolean found = false;
            for (int y = 0; y + side <= icon.getHeight() && !found; y++) {
                for (int x = 0; x + side <= icon.getWidth() && !found; x++) {
                    found = holdsSlimeAt(icon, x, y);
                }
            }
            assertTrue(found, "typhoon_updraft.png should carry Shulk's former slime texel for texel");
        }

        @Test
        void dropsThePrism() throws IOException {
            for (int rgb : opaqueColors(read("typhoon_updraft"))) {
                assertFalse(PRISM_COLORS.contains(rgb), String.format("#%06x is the former prism's color", rgb));
            }
        }

        private boolean holdsSlimeAt(BufferedImage icon, int left, int top) {
            for (int dy = 0; dy < FORMER_SHULK_SLIME.length; dy++) {
                for (int dx = 0; dx < FORMER_SHULK_SLIME[dy].length(); dx++) {
                    char texel = FORMER_SHULK_SLIME[dy].charAt(dx);
                    int argb = icon.getRGB(left + dx, top + dy);
                    boolean matches = texel == '.' ? !isOpaque(argb)
                            : isOpaque(argb) && (argb & RGB) == SLIME_COLORS.get(texel);
                    if (!matches) {
                        return false;
                    }
                }
            }
            return true;
        }
    }

    @Nested
    class GoopIcon {

        // goop-icon-is-a-block-with-slime-on-it
        private static final Set<Integer> FORMER_SLIME = Set.of(0x2f6b24, 0xc9fece, 0x51a03e, 0x8cd782);
        private static final int BLOCK_ROWS = 4;
        private static final int LEAST_BLOCK_WIDTH = 8;

        @Test
        void keepsTheFormerSlime() throws IOException {
            assertTrue(opaqueColors(read("weird_coat")).containsAll(FORMER_SLIME),
                    "the Goop icon should keep the former slime's four colors");
        }

        @Test
        void standsOnAStraightSidedBlock() throws IOException {
            BufferedImage icon = read("weird_coat");
            int bottom = lastOpaqueRow(icon);
            List<int[]> base = opaqueRuns(icon, bottom);
            int left = base.get(0)[0];
            int right = base.get(base.size() - 1)[1];
            assertTrue(right - left + 1 >= LEAST_BLOCK_WIDTH, "the block's base is too narrow");
            for (int y = bottom - BLOCK_ROWS + 1; y <= bottom; y++) {
                List<int[]> runs = opaqueRuns(icon, y);
                assertEquals(1, runs.size(), "block row " + y + " should be one solid run");
                assertEquals(left, runs.get(0)[0], "block row " + y + " should share the block's left edge");
                assertEquals(right, runs.get(0)[1], "block row " + y + " should share the block's right edge");
            }
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
