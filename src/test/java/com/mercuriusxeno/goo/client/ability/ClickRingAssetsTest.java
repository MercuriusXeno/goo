package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Click's rings draw goo's own grey ring, so the pulse red they are tinted
 * shows whole; vanilla's shriek ring is sculk teal, which a red tint only
 * darkens (decision click-draws-in-pulse-red-not-the-shrieker-visual).
 */
class ClickRingAssetsTest {

    private static final int CHANNEL_BITS = 8;
    private static final int CHANNEL_MASK = 0xFF;
    private static final int ALPHA_SHIFT = 24;
    private static final int RED_SHIFT = 16;

    @Test
    void theRingsDrawGoosOwnTextureShippedUnderItsTextures() {
        Identifier texture = GooRenderTypes.CLICK_RING_TEXTURE;
        assertAll(
                () -> assertEquals("goo", texture.getNamespace()),
                () -> assertTrue(texture.getPath().startsWith("textures/"), texture.toString()),
                () -> assertNotNull(ClickRingAssetsTest.class.getResource(resourceOf(texture)), texture.toString()));
    }

    @Test
    void everyPaintedPixelOfTheRingIsGreySoTheTintColorsItWhole() throws Exception {
        Identifier texture = GooRenderTypes.CLICK_RING_TEXTURE;
        try (InputStream in = ClickRingAssetsTest.class.getResourceAsStream(resourceOf(texture))) {
            assertNotNull(in, "texture missing on classpath: " + texture);
            BufferedImage ring = ImageIO.read(in);
            boolean painted = false;
            for (int y = 0; y < ring.getHeight(); y++) {
                for (int x = 0; x < ring.getWidth(); x++) {
                    int argb = ring.getRGB(x, y);
                    if ((argb >>> ALPHA_SHIFT) == 0) {
                        continue;
                    }
                    painted = true;
                    int red = (argb >> RED_SHIFT) & CHANNEL_MASK;
                    int green = (argb >> CHANNEL_BITS) & CHANNEL_MASK;
                    int blue = argb & CHANNEL_MASK;
                    assertTrue(red == green && green == blue, "pixel " + x + "," + y + " is not grey");
                }
            }
            assertTrue(painted, "the ring paints no pixel");
        }
    }

    private static String resourceOf(Identifier texture) {
        return "/assets/" + texture.getNamespace() + "/" + texture.getPath();
    }
}
