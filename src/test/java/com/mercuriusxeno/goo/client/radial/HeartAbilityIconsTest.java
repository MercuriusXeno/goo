package com.mercuriusxeno.goo.client.radial;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Covers that each heart ability icon draws three 7x7 hearts at one texel
 * per pixel, fanned 4 texels right and up from the front heart at the
 * bottom-left to the back heart at the top-right, read by its silhouette
 * (decision heart-icons-are-three-7x7-hearts-fanned).
 */
class HeartAbilityIconsTest {

    private static final String ABILITY_ICONS = "/assets/goo/textures/goo/ability/";
    private static final int ALPHA_SHIFT = 24;

    /** Three 7x7 hearts at (0,8), (4,4) and (8,0) of a 15x15 cluster one row down; '#' for an opaque pixel. */
    private static final String FANNED_HEARTS = String.join("\n",
            "................",
            ".........##.##..",
            "........#######.",
            "........#######.",
            "........#######.",
            ".....##.######..",
            "....#########...",
            "....########....",
            "....#######.....",
            ".##.######......",
            "#########.......",
            "########........",
            "#######.........",
            ".#####..........",
            "..###...........",
            "...#............");

    @ParameterizedTest
    @ValueSource(strings = {"blaze_kindle", "crystal_scales", "frost_iceborn", "leaf_barkskin", "nether_undead",
            "rock_stoneskin", "yore_ancient"})
    void heartIconFansThreeSevenTexelHearts(String ability) throws IOException {
        assertEquals(FANNED_HEARTS, silhouette(read(ABILITY_ICONS + ability + ".png")), ability);
    }

    private static BufferedImage read(String path) throws IOException {
        try (InputStream in = HeartAbilityIconsTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path + " is missing");
            return ImageIO.read(in);
        }
    }

    private static String silhouette(BufferedImage icon) {
        StringBuilder rows = new StringBuilder();
        for (int y = 0; y < icon.getHeight(); y++) {
            if (y > 0) {
                rows.append('\n');
            }
            for (int x = 0; x < icon.getWidth(); x++) {
                rows.append((icon.getRGB(x, y) >>> ALPHA_SHIFT) > 0 ? '#' : '.');
            }
        }
        return rows.toString();
    }
}
