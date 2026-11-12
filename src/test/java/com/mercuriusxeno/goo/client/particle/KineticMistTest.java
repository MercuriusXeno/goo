package com.mercuriusxeno.goo.client.particle;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Slick's stream sprays the kinetic mist, the gnat-stream particle Goop's slime mist is, blending
 * two blues of the slime in Slick's block texture.
 * slick-coats-the-ground-with-kinetic-speed
 * slick-spray-takes-its-blue-slime-texture-colors
 */
class KineticMistTest {

    private static final String SLICK_JSON = "/data/goo/goo_abilities/kinetic_slick.json";
    private static final String SLICK_TEXTURE = "/assets/goo/textures/block/kinetic_slick.png";
    private static final float CHANNEL_TOLERANCE = 1f / 255f;

    @Test
    void slicksStreamSpraysTheKineticMist() throws IOException {
        try (InputStream in = KineticMistTest.class.getResourceAsStream(SLICK_JSON)) {
            assertNotNull(in, SLICK_JSON);
            JsonObject slick = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("goo:kinetic_mist", slick.getAsJsonObject("delivery").get("particle").getAsString());
        }
    }

    @Test
    void theKineticMistsEndsAreColorsOfSlicksTexture() throws IOException {
        BufferedImage texture = slickTexture();
        GnatPalette mist = GnatPalette.KINETIC_MIST;

        assertTrue(textureHolds(texture, mist.lightRed(), mist.lightGreen(), mist.lightBlue()),
                "the light end should be a pixel of " + SLICK_TEXTURE + ", " + mist);
        assertTrue(textureHolds(texture, mist.darkRed(), mist.darkGreen(), mist.darkBlue()),
                "the dark end should be a pixel of " + SLICK_TEXTURE + ", " + mist);
    }

    private static BufferedImage slickTexture() throws IOException {
        try (InputStream in = KineticMistTest.class.getResourceAsStream(SLICK_TEXTURE)) {
            assertNotNull(in, SLICK_TEXTURE);
            return ImageIO.read(in);
        }
    }

    private static boolean textureHolds(BufferedImage texture, float red, float green, float blue) {
        for (int x = 0; x < texture.getWidth(); x++) {
            for (int y = 0; y < texture.getHeight(); y++) {
                int rgb = texture.getRGB(x, y);
                if (channelMatches(rgb >> 16, red) && channelMatches(rgb >> 8, green) && channelMatches(rgb, blue)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean channelMatches(int shiftedRgb, float channel) {
        return Math.abs((shiftedRgb & 0xFF) / 255f - channel) <= CHANNEL_TOLERANCE;
    }
}
