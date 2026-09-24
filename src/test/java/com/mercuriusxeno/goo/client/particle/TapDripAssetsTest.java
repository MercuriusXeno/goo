package com.mercuriusxeno.goo.client.particle;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the tap-drip's assets under decision tap-drip-own-square-particles:
 * each definition names its own sprite, and each sprite is 8x8, the
 * trail_drip's pixel scale, with its painted pixels forming a filled square.
 */
class TapDripAssetsTest {

    private static final String PARTICLES = "/assets/goo/particles/";
    private static final String SPRITES = "/assets/goo/textures/particle/";
    private static final int SPRITE_SIZE = 8;

    /**
     * Each tap-drip particle definition names its own texture alone.
     */
    @ParameterizedTest
    @CsvSource({"tap_drip.json, goo:tap_drip", "tap_drip_land.json, goo:tap_drip_land"})
    void definition_namesItsOwnTexture(String file, String texture) throws Exception {
        try (InputStream in = TapDripAssetsTest.class.getResourceAsStream(PARTICLES + file)) {
            assertNotNull(in, "Particle definition missing on classpath: " + file);
            JsonObject definition = JsonParser.parseReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("[\"" + texture + "\"]", definition.getAsJsonArray("textures").toString());
        }
    }

    /**
     * The sprite is 8x8, the bounding box of its opaque pixels is as wide as
     * it is tall, and every pixel inside that box is opaque.
     */
    @ParameterizedTest
    @ValueSource(strings = {"tap_drip.png", "tap_drip_land.png"})
    void sprite_opaquePixelsFormAFilledSquare(String file) throws Exception {
        try (InputStream in = TapDripAssetsTest.class.getResourceAsStream(SPRITES + file)) {
            assertNotNull(in, "Sprite missing on classpath: " + file);
            BufferedImage sprite = ImageIO.read(in);
            assertEquals(SPRITE_SIZE, sprite.getWidth());
            assertEquals(SPRITE_SIZE, sprite.getHeight());
            int minX = SPRITE_SIZE;
            int minY = SPRITE_SIZE;
            int maxX = -1;
            int maxY = -1;
            for (int y = 0; y < SPRITE_SIZE; y++) {
                for (int x = 0; x < SPRITE_SIZE; x++) {
                    if (isOpaque(sprite, x, y)) {
                        minX = Math.min(minX, x);
                        minY = Math.min(minY, y);
                        maxX = Math.max(maxX, x);
                        maxY = Math.max(maxY, y);
                    }
                }
            }
            assertTrue(maxX >= 0, "sprite paints no pixel");
            assertEquals(maxX - minX, maxY - minY, "painted box is not square");
            for (int y = minY; y <= maxY; y++) {
                for (int x = minX; x <= maxX; x++) {
                    assertTrue(isOpaque(sprite, x, y), "hole in the square at " + x + "," + y);
                }
            }
        }
    }

    private static boolean isOpaque(BufferedImage sprite, int x, int y) {
        return (sprite.getRGB(x, y) >>> 24) != 0;
    }
}
