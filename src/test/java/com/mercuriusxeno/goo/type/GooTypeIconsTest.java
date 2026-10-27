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
    private static final String QUANTUM_TOUCHED_UP = "/expected/goo/type/quantum_touched_up.png";
    private static final String QUANTUM_TYPE_JSON = "/data/goo/goo/goo_type/quantum.json";
    private static final float ORANGE_HUE_CEILING = 45f / 360f;
    private static final float ORANGE_SATURATION_FLOOR = 0.4f;
    private static final float ORANGE_HUE_TOLERANCE = 8f / 360f;

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
    class Quantum {

        // quantum-is-gray-half-white-half-black: the touched-up icon in the brighter, oranger orange
        @Test
        void quantumIconsOrangePixelsWearItsBright() throws IOException {
            float brightHue = hsb(quantumBright())[0];
            BufferedImage touchedUp = readImage(QUANTUM_TOUCHED_UP);
            BufferedImage quantum = readIcon("quantum");
            int orangePixels = 0;
            for (int y = 0; y < touchedUp.getHeight(); y++) {
                for (int x = 0; x < touchedUp.getWidth(); x++) {
                    if (isOrange(touchedUp.getRGB(x, y))) {
                        orangePixels++;
                        float gap = Math.abs(hsb(quantum.getRGB(x, y))[0] - brightHue);
                        assertTrue(Math.min(gap, 1f - gap) <= ORANGE_HUE_TOLERANCE,
                                "orange at " + x + "," + y + " sits off quantum's bright");
                    } else {
                        assertEquals(touchedUp.getRGB(x, y), quantum.getRGB(x, y), "quantum differs at " + x + "," + y);
                    }
                }
            }
            assertTrue(orangePixels > 0, "the touched-up icon holds no orange");
        }

        private static boolean isOrange(int argb) {
            float[] hsb = hsb(argb);
            return argb >>> 24 != 0 && hsb[0] <= ORANGE_HUE_CEILING && hsb[1] >= ORANGE_SATURATION_FLOOR;
        }

        private static int quantumBright() throws IOException {
            try (InputStream in = GooTypeIconsTest.class.getResourceAsStream(QUANTUM_TYPE_JSON)) {
                assertNotNull(in, "missing on the classpath: " + QUANTUM_TYPE_JSON);
                String json = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                return Integer.parseInt(com.google.gson.JsonParser.parseString(json).getAsJsonObject()
                        .get("bright").getAsString(), 16);
            }
        }
    }

    private static float[] hsb(int rgb) {
        return Color.RGBtoHSB(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, null);
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
