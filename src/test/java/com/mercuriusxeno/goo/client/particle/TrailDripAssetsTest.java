package com.mercuriusxeno.goo.client.particle;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests the trail-drip's assets under decision tap-drip-own-square-particles:
 * both particle definitions name the 2x3 trail_drip sprite, which kept its pixels.
 */
class TrailDripAssetsTest {

    private static final String PARTICLES = "/assets/goo/particles/";
    private static final String TRAIL_DRIP_PNG = "/assets/goo/textures/particle/trail_drip.png";
    private static final int SPRITE_SIZE = 8;
    private static final int OPAQUE_MIN_X = 3;
    private static final int OPAQUE_MAX_X = 4;
    private static final int OPAQUE_MIN_Y = 2;
    private static final int OPAQUE_MAX_Y = 4;

    /**
     * Each trail-drip particle definition names the texture goo:trail_drip alone.
     */
    @ParameterizedTest
    @ValueSource(strings = {"trail_drip.json", "trail_drip_land.json"})
    void definition_namesTrailDripTexture(String file) throws Exception {
        try (InputStream in = TrailDripAssetsTest.class.getResourceAsStream(PARTICLES + file)) {
            assertNotNull(in, "Particle definition missing on classpath: " + file);
            JsonObject definition = JsonParser.parseReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("[\"goo:trail_drip\"]", definition.getAsJsonArray("textures").toString());
        }
    }

    /**
     * The sprite is 8x8 and its opaque pixels are exactly the 2-wide by
     * 3-tall block at x 3-4, y 2-4.
     */
    @Test
    void sprite_opaquePixelsAreTheTwoByThreeBlock() throws Exception {
        try (InputStream in = TrailDripAssetsTest.class.getResourceAsStream(TRAIL_DRIP_PNG)) {
            assertNotNull(in, "Sprite missing on classpath: " + TRAIL_DRIP_PNG);
            BufferedImage sprite = ImageIO.read(in);
            assertEquals(SPRITE_SIZE, sprite.getWidth());
            assertEquals(SPRITE_SIZE, sprite.getHeight());
            for (int y = 0; y < SPRITE_SIZE; y++) {
                for (int x = 0; x < SPRITE_SIZE; x++) {
                    boolean inBlock = x >= OPAQUE_MIN_X && x <= OPAQUE_MAX_X
                            && y >= OPAQUE_MIN_Y && y <= OPAQUE_MAX_Y;
                    boolean opaque = (sprite.getRGB(x, y) >>> 24) != 0;
                    assertEquals(inBlock, opaque, "pixel " + x + "," + y);
                }
            }
        }
    }
}
