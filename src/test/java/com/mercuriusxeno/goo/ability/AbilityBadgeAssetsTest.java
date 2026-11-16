package com.mercuriusxeno.goo.ability;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The held and residual badges each ship a sprite the size of the channeled sprite beside them and a
 * name in en_us.json, both read from the classpath.
 * decision held-badge-marks-abilities-that-prepare-and-throw
 * decision residual-badge-is-a-goo-splat
 */
class AbilityBadgeAssetsTest {

    private static final String BADGE_TEXTURES = "/assets/goo/textures/goo/badge/";
    private static final String EN_US_JSON = "/assets/goo/lang/en_us.json";

    @ParameterizedTest
    @CsvSource({"HELD", "RESIDUAL"})
    void badgeSpriteMatchesTheChanneledSpriteSize(AbilityBadge badge) throws IOException {
        BufferedImage sprite = readSprite(badge);
        BufferedImage channeled = readSprite(AbilityBadge.CHANNELED);
        assertEquals(channeled.getWidth(), sprite.getWidth(), badge.getSerializedName() + ".png width");
        assertEquals(channeled.getHeight(), sprite.getHeight(), badge.getSerializedName() + ".png height");
    }

    @ParameterizedTest
    @CsvSource({"HELD, Held", "RESIDUAL, Residual"})
    void enUsNamesTheBadge(AbilityBadge badge, String name) throws IOException {
        String key = "goo.badge." + badge.getSerializedName();
        try (InputStream in = AbilityBadgeAssetsTest.class.getResourceAsStream(EN_US_JSON)) {
            assertNotNull(in, EN_US_JSON + " is not on the classpath");
            JsonObject lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            assertNotNull(lang.get(key), "en_us.json names no " + key);
            assertEquals(name, lang.get(key).getAsString());
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
