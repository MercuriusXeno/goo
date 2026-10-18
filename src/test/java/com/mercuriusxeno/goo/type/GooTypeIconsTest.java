package com.mercuriusxeno.goo.type;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
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
