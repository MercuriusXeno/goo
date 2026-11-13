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
 * Slick's stream throws Slick's spray drip, the mod's blocky goo drip blending
 * from its texture's light slime blue, 6A9AE0, to its dark slime blue, 2C7DC1.
 * goop-and-slick-spray-throws-goo-drips-that-splat
 * slick-spray-takes-its-blue-slime-texture-colors
 */
class SlickSprayDripTest {

    private static final String SLICK_JSON = "/data/goo/goo_abilities/kinetic_slick.json";
    private static final String SLICK_TEXTURE = "/assets/goo/textures/block/kinetic_slick.png";
    private static final float CHANNEL_TOLERANCE = 1f / 255f;

    @Test
    void slicksStreamThrowsSlicksSprayDrip() throws IOException {
        try (InputStream in = SlickSprayDripTest.class.getResourceAsStream(SLICK_JSON)) {
            assertNotNull(in, SLICK_JSON);
            JsonObject slick = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("goo:slick_spray_drip", slick.getAsJsonObject("delivery").get("particle").getAsString());
        }
    }

    @Test
    void slicksSprayDripRunsFromTheLightSlimeBlueToTheDarkSlimeBlue() {
        BlendPalette blues = BlendPalette.SLICK_SLIME_BLUES;

        assertEquals(0x6A9AE0, rgbOf(blues.lightRed(), blues.lightGreen(), blues.lightBlue()), blues.toString());
        assertEquals(0x2C7DC1, rgbOf(blues.darkRed(), blues.darkGreen(), blues.darkBlue()), blues.toString());
    }

    @Test
    void slicksSprayDripsEndsAreColorsOfSlicksTexture() throws IOException {
        BufferedImage texture = slickTexture();
        BlendPalette blues = BlendPalette.SLICK_SLIME_BLUES;

        assertTrue(textureHolds(texture, blues.lightRed(), blues.lightGreen(), blues.lightBlue()),
                "the light end should be a pixel of " + SLICK_TEXTURE + ", " + blues);
        assertTrue(textureHolds(texture, blues.darkRed(), blues.darkGreen(), blues.darkBlue()),
                "the dark end should be a pixel of " + SLICK_TEXTURE + ", " + blues);
    }

    private static int rgbOf(float red, float green, float blue) {
        return Math.round(red * 255) << 16 | Math.round(green * 255) << 8 | Math.round(blue * 255);
    }

    private static BufferedImage slickTexture() throws IOException {
        try (InputStream in = SlickSprayDripTest.class.getResourceAsStream(SLICK_TEXTURE)) {
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
