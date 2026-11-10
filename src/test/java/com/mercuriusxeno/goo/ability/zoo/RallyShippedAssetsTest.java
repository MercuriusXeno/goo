package com.mercuriusxeno.goo.ability.zoo;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.AbilityJson;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers what Rally ships: its behaviors are the rally of nearby peaceful mobs
 * alone, the red fist a rallied mob wears, and the Zoo Brew effect a cast
 * Rally lays shows Rally's own icon.
 * rally-drops-its-sound-and-gets-its-sprite
 */
class RallyShippedAssetsTest {

    private static final String RALLY_ICON = "/assets/goo/textures/goo/ability/zoo_rally.png";
    private static final String ZOO_BREW_ICON = "/assets/goo/textures/mob_effect/zoo_held.png";
    private static final String RALLY_FIST = "/assets/goo/textures/entity/rally_fist.png";
    private static final int SIDE = 16;
    private static final int FIST_BODY_X = 7;
    private static final int FIST_BODY_Y = 9;
    private static final int RED_SHIFT = 16;
    private static final int GREEN_SHIFT = 8;
    private static final int CHANNEL = 0xFF;

    @Test
    void rallyBehaviorsAreTheEntitiesRallyAlone() {
        JsonElement behaviors = JsonParser.parseString(AbilityJson.read("zoo_rally"))
                .getAsJsonObject().get("behaviors");

        List<String> types = StreamSupport.stream(behaviors.getAsJsonArray().spliterator(), false)
                .map(behavior -> behavior.getAsJsonObject().get("type").getAsString())
                .toList();

        assertEquals(List.of("entities"), types);
    }

    /**
     * rallied-mobs-wear-a-red-clenched-fist: the fist and outline mark a rallied mob, so the steps lay no glisten.
     */
    @Test
    void rallyEntitiesStepsAreTheRallyAlone() {
        JsonElement behaviors = JsonParser.parseString(AbilityJson.read("zoo_rally"))
                .getAsJsonObject().get("behaviors");

        List<String> steps = StreamSupport.stream(behaviors.getAsJsonArray().get(0).getAsJsonObject()
                        .getAsJsonArray("steps").spliterator(), false)
                .map(step -> step.getAsJsonObject().get("type").getAsString())
                .toList();

        assertEquals(List.of("rally"), steps);
    }

    @Test
    void rallyFistIsARedSixteenSquare() throws IOException {
        BufferedImage fist = read(RALLY_FIST);

        assertEquals(SIDE, fist.getWidth());
        assertEquals(SIDE, fist.getHeight());
        int body = fist.getRGB(FIST_BODY_X, FIST_BODY_Y);
        assertTrue((body >> RED_SHIFT & CHANNEL) > (body >> GREEN_SHIFT & CHANNEL) * 2);
    }

    @Test
    void zooBrewEffectDrawsRallysIcon() throws IOException {
        BufferedImage brew = read(ZOO_BREW_ICON);
        BufferedImage rally = read(RALLY_ICON);

        assertEquals(SIDE, brew.getWidth());
        assertEquals(SIDE, brew.getHeight());
        assertArrayEquals(pixels(rally), pixels(brew));
    }

    private static BufferedImage read(String path) throws IOException {
        try (InputStream in = RallyShippedAssetsTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path + " is missing");
            return ImageIO.read(in);
        }
    }

    private static int[] pixels(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }
}
