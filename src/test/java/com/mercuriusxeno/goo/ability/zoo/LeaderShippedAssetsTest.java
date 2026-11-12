package com.mercuriusxeno.goo.ability.zoo;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Covers what Leader ships beside its JSON: its 16x16 icon, the Zoo held
 * effect drawing that icon, and its name and description in the lang.
 * leader-is-zoos-brew-ability
 */
class LeaderShippedAssetsTest {

    private static final String LEADER_ICON = "/assets/goo/textures/goo/ability/zoo_leader.png";
    private static final String ZOO_BREW_ICON = "/assets/goo/textures/mob_effect/zoo_held.png";
    private static final String EN_US = "/assets/goo/lang/en_us.json";
    private static final String LEADER_NAME = "goo.ability.zoo.leader";
    private static final String LEADER_DESCRIPTION = LEADER_NAME + ".description";
    private static final int SIDE = 16;

    @Test
    void zooBrewEffectDrawsLeadersIcon() throws IOException {
        BufferedImage brew = read(ZOO_BREW_ICON);
        BufferedImage leader = read(LEADER_ICON);

        assertEquals(SIDE, leader.getWidth());
        assertEquals(SIDE, leader.getHeight());
        assertArrayEquals(pixels(leader), pixels(brew));
    }

    @Test
    void langNamesLeaderAndDescribesIt() throws IOException {
        JsonObject lang;
        try (InputStream in = LeaderShippedAssetsTest.class.getResourceAsStream(EN_US)) {
            assertNotNull(in, EN_US + " is missing");
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                lang = JsonParser.parseReader(reader).getAsJsonObject();
            }
        }

        assertEquals("Leader", lang.has(LEADER_NAME) ? lang.get(LEADER_NAME).getAsString() : null);
        assertFalse(!lang.has(LEADER_DESCRIPTION) || lang.get(LEADER_DESCRIPTION).getAsString().isBlank(),
                LEADER_DESCRIPTION + " holds no description");
    }

    private static BufferedImage read(String path) throws IOException {
        try (InputStream in = LeaderShippedAssetsTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path + " is missing");
            return ImageIO.read(in);
        }
    }

    private static int[] pixels(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }
}
