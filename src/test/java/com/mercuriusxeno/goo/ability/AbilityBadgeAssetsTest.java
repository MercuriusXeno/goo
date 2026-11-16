package com.mercuriusxeno.goo.ability;

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

/**
 * The held badge ships a sprite the size of the channeled sprite beside it and a name in en_us.json,
 * both read from the classpath.
 * decision held-badge-marks-abilities-that-prepare-and-throw
 */
class AbilityBadgeAssetsTest {

    private static final String BADGE_TEXTURES = "/assets/goo/textures/goo/badge/";
    private static final String EN_US_JSON = "/assets/goo/lang/en_us.json";

    @Test
    void heldSpriteMatchesTheChanneledSpriteSize() throws IOException {
        BufferedImage held = readSprite(AbilityBadge.HELD);
        BufferedImage channeled = readSprite(AbilityBadge.CHANNELED);
        assertEquals(channeled.getWidth(), held.getWidth(), "held.png width");
        assertEquals(channeled.getHeight(), held.getHeight(), "held.png height");
    }

    @Test
    void enUsNamesTheHeldBadge() throws IOException {
        try (InputStream in = AbilityBadgeAssetsTest.class.getResourceAsStream(EN_US_JSON)) {
            assertNotNull(in, EN_US_JSON + " is not on the classpath");
            JsonObject lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            assertNotNull(lang.get("goo.badge.held"), "en_us.json names no goo.badge.held");
            assertEquals("Held", lang.get("goo.badge.held").getAsString());
        }
    }

    private static BufferedImage readSprite(AbilityBadge badge) throws IOException {
        String resource = BADGE_TEXTURES + badge.getSerializedName() + ".png";
        try (InputStream in = AbilityBadgeAssetsTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, resource + " is not on the classpath");
            return ImageIO.read(in);
        }
    }
}
