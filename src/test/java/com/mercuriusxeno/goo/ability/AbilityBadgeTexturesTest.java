package com.mercuriusxeno.goo.ability;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Every badge texture draws its opaque pixels across prism's 10x10 footprint, read
 * through the class loader so the test never touches the filesystem.
 * channeling-badge-no-fatter-than-other-icons
 */
class AbilityBadgeTexturesTest {

    private static final int PRISM_FOOTPRINT = 10;

    @ParameterizedTest
    @EnumSource(AbilityBadge.class)
    void badgeSpansPrismFootprint(AbilityBadge badge) throws IOException {
        BufferedImage texture = readBadgeTexture(badge);
        int[] box = opaqueBoundingBox(texture);
        int width = box[2] - box[0] + 1;
        int height = box[3] - box[1] + 1;
        assertAll(badge.getSerializedName(),
                () -> assertEquals(PRISM_FOOTPRINT, width, "width"),
                () -> assertEquals(PRISM_FOOTPRINT, height, "height"));
    }

    private static BufferedImage readBadgeTexture(AbilityBadge badge) throws IOException {
        String path = "/assets/goo/textures/goo/badge/" + badge.getSerializedName() + ".png";
        try (InputStream stream = AbilityBadgeTexturesTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return ImageIO.read(stream);
        }
    }

    /** Answers min x, min y, max x, max y of the pixels whose alpha is above zero. */
    private static int[] opaqueBoundingBox(BufferedImage texture) {
        int[] box = {Integer.MAX_VALUE, Integer.MAX_VALUE, -1, -1};
        for (int y = 0; y < texture.getHeight(); y++) {
            for (int x = 0; x < texture.getWidth(); x++) {
                if ((texture.getRGB(x, y) >>> 24) != 0) {
                    box[0] = Math.min(box[0], x);
                    box[1] = Math.min(box[1], y);
                    box[2] = Math.max(box[2], x);
                    box[3] = Math.max(box[3], y);
                }
            }
        }
        return box;
    }
}
