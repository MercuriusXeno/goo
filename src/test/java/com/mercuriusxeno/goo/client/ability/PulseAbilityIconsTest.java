package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Click's ability icon is drawn in Pulse's reds, as Zap's and Tick's are:
 * no painted pixel holds more green or blue than red
 * (decision click-draws-in-pulse-red-not-the-shrieker-visual).
 */
class PulseAbilityIconsTest {

    private static final String ICONS = "/assets/goo/textures/goo/ability/";
    private static final int CHANNEL_BITS = 8;
    private static final int CHANNEL_MASK = 0xFF;
    private static final int ALPHA_SHIFT = 24;
    private static final int RED_SHIFT = 16;

    @ParameterizedTest
    @ValueSource(strings = {"pulse_click", "pulse_zap", "pulse_tick"})
    void everyPaintedPixelIsAsRedAsItIsGreenOrBlue(String icon) throws Exception {
        try (InputStream in = PulseAbilityIconsTest.class.getResourceAsStream(ICONS + icon + ".png")) {
            assertNotNull(in, icon + " missing on classpath");
            BufferedImage image = ImageIO.read(in);
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int argb = image.getRGB(x, y);
                    if ((argb >>> ALPHA_SHIFT) == 0) {
                        continue;
                    }
                    int red = (argb >> RED_SHIFT) & CHANNEL_MASK;
                    int green = (argb >> CHANNEL_BITS) & CHANNEL_MASK;
                    int blue = argb & CHANNEL_MASK;
                    assertTrue(red >= green && red >= blue,
                            String.format("%s pixel %d,%d is %06X", icon, x, y, argb & 0xFFFFFF));
                }
            }
        }
    }
}
