package com.mercuriusxeno.goo.client.workbench;

import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests the pointer hand's image as it ships: a transparent background and a hot spot on an opaque pixel (decision
 * shift-over-a-link-shows-a-pixel-pointer-hand).
 */
class PointerHandTest {
    private static final int ALPHA_SHIFT = 24;
    private static final int OPAQUE = 0xFF;

    /**
     * The shipped PNG's corner is transparent, and the pixel PointerHand names as its hot spot is opaque.
     */
    @Test
    void handShipsTransparentWithItsHotSpotOnAnOpaquePixel() throws IOException {
        BufferedImage hand;
        try (InputStream in = PointerHandTest.class.getResourceAsStream(
                "/assets/goo/" + PointerHand.TEXTURE_PATH)) {
            assertNotNull(in, PointerHand.TEXTURE_PATH + " does not ship");
            hand = ImageIO.read(in);
        }
        assertEquals(0, hand.getRGB(0, 0) >>> ALPHA_SHIFT);
        assertEquals(OPAQUE, hand.getRGB(PointerHand.HOT_X, PointerHand.HOT_Y) >>> ALPHA_SHIFT);
    }
}
