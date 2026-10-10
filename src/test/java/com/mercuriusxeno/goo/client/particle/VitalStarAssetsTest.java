package com.mercuriusxeno.goo.client.particle;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The healing star's sprite is goo's own grey copy of the bone-meal star,
 * so the vital pink the particle tints it with shows whole; vanilla's star
 * is drawn green with no red in it, which a tint can only darken
 * (decision vitality-waves-regenerate-and-court).
 */
class VitalStarAssetsTest {

    private static final String DEFINITION = "/assets/goo/particles/vital_star.json";
    private static final String SPRITE = "/assets/goo/textures/particle/vital_star.png";
    private static final String OWN_SPRITE = "[\"goo:vital_star\"]";
    private static final int CHANNEL_BITS = 8;
    private static final int CHANNEL_MASK = 0xFF;
    private static final int ALPHA_SHIFT = 24;
    private static final int RED_SHIFT = 16;

    @Test
    void theStarNamesGoosOwnSprite() throws Exception {
        try (InputStream in = VitalStarAssetsTest.class.getResourceAsStream(DEFINITION)) {
            assertNotNull(in, "Particle definition missing on classpath");
            JsonObject definition = JsonParser.parseReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals(OWN_SPRITE, definition.getAsJsonArray("textures").toString());
        }
    }

    @Test
    void everyPaintedPixelOfTheStarIsGreySoTheTintColorsItWhole() throws Exception {
        try (InputStream in = VitalStarAssetsTest.class.getResourceAsStream(SPRITE)) {
            assertNotNull(in, "Sprite missing on classpath");
            BufferedImage sprite = ImageIO.read(in);
            boolean painted = false;
            for (int y = 0; y < sprite.getHeight(); y++) {
                for (int x = 0; x < sprite.getWidth(); x++) {
                    int argb = sprite.getRGB(x, y);
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
            assertTrue(painted, "sprite paints no pixel");
        }
    }
}
