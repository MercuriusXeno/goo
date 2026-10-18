package com.mercuriusxeno.goo.type;

import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the pictures the goo type icons hold, read as pixels through the classpath.
 */
class GooTypeIconsTest {

    private static final String TYPE_ICON_FORMAT = "/assets/goo/textures/goo/type/%s.png";
    private static final String AEON_HOURGLASS = "/expected/goo/type/aeon_hourglass.png";
    private static final float GRAY_SATURATION_CEILING = 0.12f;
    private static final float BROWN_HUE_LOW = 15f / 360f;
    private static final float BROWN_HUE_HIGH = 45f / 360f;
    /** The least saturated brown the old rock icon held, #884b2b. */
    private static final float OLD_ROCK_BROWN_SATURATION = 0.68f;
    private static final int ICON_SIZE = 11;
    private static final int MIN_MASK_DISTANCE = 12;
    private static final float HUE_GAP = 25f / 360f;
    private static final float COLORED_SATURATION = 0.25f;
    private static final float COLORED_BRIGHTNESS = 0.1f;
    /** The icons redrawn by hand, each from its own decision, on no shared disc. */
    private static final List<String> REDRAWN = List.of("astral", "deep", "infinity", "jelly");

    @Nested
    class Yore {

        // yore-takes-aeons-hourglass-icon
        @Test
        void yoreIconIsAeonsHourglass() throws IOException {
            BufferedImage hourglass = readImage(AEON_HOURGLASS);
            BufferedImage yore = readIcon("yore");
            assertEquals(hourglass.getWidth(), yore.getWidth());
            assertEquals(hourglass.getHeight(), yore.getHeight());
            for (int y = 0; y < hourglass.getHeight(); y++) {
                for (int x = 0; x < hourglass.getWidth(); x++) {
                    assertEquals(hourglass.getRGB(x, y), yore.getRGB(x, y), "yore differs at " + x + "," + y);
                }
            }
        }
    }

    @Nested
    class Rock {

        // rock-icon-is-two-tone-brown-and-gray
        @Test
        void rockIconHoldsADesaturatedBrownAndAGray() throws IOException {
            BufferedImage rock = readIcon("rock");
            int grayPixels = 0;
            int brownPixels = 0;
            for (int y = 0; y < rock.getHeight(); y++) {
                for (int x = 0; x < rock.getWidth(); x++) {
                    int argb = rock.getRGB(x, y);
                    if (argb >>> 24 == 0) {
                        continue;
                    }
                    float[] hsb = Color.RGBtoHSB(argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF, null);
                    if (hsb[1] < GRAY_SATURATION_CEILING) {
                        grayPixels++;
                    } else if (hsb[0] >= BROWN_HUE_LOW && hsb[0] <= BROWN_HUE_HIGH) {
                        brownPixels++;
                        assertTrue(hsb[1] < OLD_ROCK_BROWN_SATURATION,
                                "brown at " + x + "," + y + " is as saturated as the old icon's");
                    }
                }
            }
            assertTrue(grayPixels > 0, "rock holds no gray");
            assertTrue(brownPixels > 0, "rock holds no brown");
        }
    }

    @Nested
    class Redrawn {

        static Stream<String> redrawnIcons() {
            return REDRAWN.stream();
        }

        @ParameterizedTest
        @MethodSource("redrawnIcons")
        void maskDiffersFromEveryOtherIcon(String typeId) throws IOException {
            boolean[] mask = opaqueMask(readIcon(typeId));
            for (ResourceKey<GooTypeDefinition> other : GooTypes.BUNDLED) {
                String otherId = GooTypes.id(other);
                if (otherId.equals(typeId)) {
                    continue;
                }
                boolean[] otherMask = opaqueMask(readIcon(otherId));
                int distance = 0;
                for (int i = 0; i < mask.length; i++) {
                    distance += mask[i] == otherMask[i] ? 0 : 1;
                }
                assertTrue(distance >= MIN_MASK_DISTANCE,
                        typeId + "'s mask differs from " + otherId + "'s in only " + distance + " pixels");
            }
        }

        @ParameterizedTest
        @MethodSource("redrawnIcons")
        void holdsAtLeastTwoHues(String typeId) throws IOException {
            assertTrue(countHueClusters(readIcon(typeId)) >= 2, typeId + " holds a single hue");
        }
    }

    private static boolean[] opaqueMask(BufferedImage icon) {
        boolean[] mask = new boolean[ICON_SIZE * ICON_SIZE];
        for (int y = 0; y < ICON_SIZE; y++) {
            for (int x = 0; x < ICON_SIZE; x++) {
                mask[y * ICON_SIZE + x] = icon.getRGB(x, y) >>> 24 != 0;
            }
        }
        return mask;
    }

    /**
     * Counts the hue families among an icon's colored pixels: hues sorted round the wheel,
     * a new family starting at each gap of HUE_GAP or wider.
     */
    private static int countHueClusters(BufferedImage icon) {
        List<Float> hues = new ArrayList<>();
        for (int y = 0; y < icon.getHeight(); y++) {
            for (int x = 0; x < icon.getWidth(); x++) {
                int argb = icon.getRGB(x, y);
                float[] hsb = Color.RGBtoHSB(argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF, null);
                if (argb >>> 24 != 0 && hsb[1] >= COLORED_SATURATION && hsb[2] >= COLORED_BRIGHTNESS) {
                    hues.add(hsb[0]);
                }
            }
        }
        Collections.sort(hues);
        int clusters = 0;
        for (int i = 0; i < hues.size(); i++) {
            float previous = i == 0 ? hues.getLast() - 1f : hues.get(i - 1);
            clusters += hues.get(i) - previous >= HUE_GAP ? 1 : 0;
        }
        return hues.isEmpty() ? 0 : Math.max(clusters, 1);
    }

    private static BufferedImage readIcon(String typeId) throws IOException {
        return readImage(TYPE_ICON_FORMAT.formatted(typeId));
    }

    private static BufferedImage readImage(String path) throws IOException {
        try (InputStream in = GooTypeIconsTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing on the classpath: " + path);
            return ImageIO.read(in);
        }
    }
}
