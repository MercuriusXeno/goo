package com.mercuriusxeno.goo.client.radial;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Covers that each heart ability icon draws, front to back, the three hearts
 * the operator listed for its ability: what the overshield looks like over
 * normal hearts, the normal heart under it, and what an empty heart turns
 * into. Every heart texel is read where no heart in front covers it and no
 * seam texel beside the heart in front replaces it
 * (decision heart-icons-are-three-7x7-hearts-fanned).
 */
class HeartAbilityIconHeartsTest {

    private static final String ABILITY_ICONS = "/assets/goo/textures/goo/ability/";
    private static final String HUD_HEARTS = "/assets/goo/textures/gui/sprites/hud/heart/";
    private static final int ALPHA_SHIFT = 24;
    private static final int SIDE = 7;
    /** Each heart's top-left in the icon, front heart first. */
    private static final int[][] OFFSETS = {{0, 9}, {4, 5}, {8, 1}};
    private static final int[][] NEIGHBOURS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    /** Vanilla's red heart inside its outline: R the red, H its highlight, S its shade. */
    private static final String[] VANILLA_RED = {
        ".RR.RR.",
        "RHRRRRR",
        "RRRRRRR",
        "SRRRRRS",
        ".SRRRS.",
        "..SRS..",
        "...S...",
    };
    private static final int RED = 0xFFFF1313;
    private static final int RED_HIGHLIGHT = 0xFFFFC8C8;
    private static final int RED_SHADE = 0xFFBB1313;
    /** Vanilla's empty heart container's fill. */
    private static final int EMPTY = 0xFF282828;
    /** The half heart's columns, its tip's included, inside the 7x7 heart. */
    private static final int HALF_COLUMNS = 4;

    /** One heart in an icon: a whole heart, with a half heart laid over its left side or none. */
    private record Heart(Layer base, Layer over) {
    }

    /** A 7x7 heart layer: its texel at (x, y), zero where it lays nothing. */
    private interface Layer {
        int at(int x, int y);
    }

    private static Layer sprite(String name) throws IOException {
        BufferedImage image = read(HUD_HEARTS + name + ".png");
        return (x, y) -> image.getRGB(x + 1, y + 1);
    }

    private static Layer red() {
        return (x, y) -> switch (VANILLA_RED[y].charAt(x)) {
            case 'R' -> RED;
            case 'H' -> RED_HIGHLIGHT;
            case 'S' -> RED_SHADE;
            default -> 0;
        };
    }

    private static Layer redHalf() {
        Layer red = red();
        return (x, y) -> x < HALF_COLUMNS ? red.at(x, y) : 0;
    }

    private static Layer empty() {
        Layer red = red();
        return (x, y) -> red.at(x, y) == 0 ? 0 : EMPTY;
    }

    private static Layer none() {
        return (x, y) -> 0;
    }

    private static Heart whole(Layer base) {
        return new Heart(base, none());
    }

    private static Heart halfOver(Layer over, Layer base) {
        return new Heart(base, over);
    }

    static Stream<Arguments> listedHearts() throws IOException {
        return Stream.of(
                Arguments.of("blaze_kindle", List.of(whole(sprite("ember_full")), whole(sprite("ash_full")),
                        halfOver(sprite("ash_half"), empty()))),
                Arguments.of("crystal_scales", List.of(whole(sprite("crystal_full")),
                        halfOver(sprite("crystal_half"), red()), halfOver(redHalf(), empty()))),
                Arguments.of("frost_iceborn", List.of(whole(sprite("ice_full")),
                        halfOver(sprite("ice_half"), red()), halfOver(redHalf(), empty()))),
                Arguments.of("leaf_barkskin", List.of(whole(sprite("bark_full")),
                        halfOver(sprite("bark_half"), red()), halfOver(redHalf(), empty()))),
                Arguments.of("nether_undead", List.of(whole(sprite("zombie_full")), whole(sprite("nether_full")),
                        halfOver(sprite("nether_half"), empty()))),
                Arguments.of("rock_stoneskin", List.of(whole(red()), whole(sprite("stone_full")),
                        halfOver(sprite("stone_half"), empty()))),
                Arguments.of("yore_ancient", List.of(whole(sprite("ancient_full")), whole(red()), whole(empty()))));
    }

    @ParameterizedTest
    @MethodSource("listedHearts")
    void iconDrawsTheListedHeartsFrontToBack(String ability, List<Heart> hearts) throws IOException {
        BufferedImage icon = read(ABILITY_ICONS + ability + ".png");
        for (int position = 0; position < hearts.size(); position++) {
            Heart heart = hearts.get(position);
            for (int y = 0; y < SIDE; y++) {
                for (int x = 0; x < SIDE; x++) {
                    int iconX = OFFSETS[position][0] + x;
                    int iconY = OFFSETS[position][1] + y;
                    if (!inHeart(x, y) || hiddenByHeartsInFront(position, iconX, iconY)) {
                        continue;
                    }
                    int over = heart.over().at(x, y);
                    int expected = (over >>> ALPHA_SHIFT) > 0 ? over : heart.base().at(x, y);
                    assertEquals(Integer.toHexString(expected), Integer.toHexString(icon.getRGB(iconX, iconY)),
                            ability + " heart " + (position + 1) + " texel " + x + "," + y);
                }
            }
        }
    }

    private static boolean inHeart(int x, int y) {
        return VANILLA_RED[y].charAt(x) != '.';
    }

    /** A texel a heart in front covers, or the seam texel beside one. */
    private static boolean hiddenByHeartsInFront(int position, int iconX, int iconY) {
        for (int front = 0; front < position; front++) {
            if (coveredBy(front, iconX, iconY)) {
                return true;
            }
            for (int[] step : NEIGHBOURS) {
                if (coveredBy(front, iconX + step[0], iconY + step[1])) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean coveredBy(int position, int iconX, int iconY) {
        int x = iconX - OFFSETS[position][0];
        int y = iconY - OFFSETS[position][1];
        return x >= 0 && x < SIDE && y >= 0 && y < SIDE && inHeart(x, y);
    }

    private static BufferedImage read(String path) throws IOException {
        try (InputStream in = HeartAbilityIconHeartsTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path + " is missing");
            return ImageIO.read(in);
        }
    }
}
