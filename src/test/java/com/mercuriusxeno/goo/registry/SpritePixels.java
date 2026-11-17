package com.mercuriusxeno.goo.registry;

import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Reads a block sprite off the classpath and its pixels as the file stores them. */
final class SpritePixels {

    private SpritePixels() {
    }

    static BufferedImage image(String path) throws IOException {
        try (InputStream stream = SpritePixels.class.getResourceAsStream(path)) {
            assertNotNull(stream, path + " is not on the classpath");
            BufferedImage image = ImageIO.read(stream);
            assertNotNull(image, path + " is no image");
            return image;
        }
    }

    static BufferedImage gooBlock(String sprite) throws IOException {
        return image("/assets/goo/textures/block/" + sprite + ".png");
    }

    /**
     * The pixel as the file stores it. getRGB reads a greyscale PNG's grey as linear light and brightens it, so stone's
     * 0x7f reads as 0xbb; a grey pixel is read from the raster instead.
     */
    static int storedArgb(BufferedImage image, int x, int y) {
        if (image.getColorModel().getColorSpace().getType() != ColorSpace.TYPE_GRAY) {
            return image.getRGB(x, y);
        }
        Raster raster = image.getRaster();
        int max = (1 << raster.getSampleModel().getSampleSize(0)) - 1;
        int grey = raster.getSample(x, y, 0) * 0xff / max;
        int alpha = raster.getNumBands() > 1 ? raster.getSample(x, y, 1) * 0xff / max : 0xff;
        return alpha << 24 | grey << 16 | grey << 8 | grey;
    }
}
