package com.mercuriusxeno.goo.ability.zoo;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.AbilityJson;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers what Rally ships: a mob ability thrown at one target whose steps are
 * the rally alone, and the red fist a rallied mob wears.
 * rally-drops-its-sound-and-gets-its-sprite
 */
class RallyShippedAssetsTest {

    private static final String RALLY_FIST = "/assets/goo/textures/entity/rally_fist.png";
    private static final int SIDE = 16;
    private static final int FIST_BODY_X = 7;
    private static final int FIST_BODY_Y = 9;
    private static final int RED_SHIFT = 16;
    private static final int GREEN_SHIFT = 8;
    private static final int CHANNEL = 0xFF;
    private static final double HALF = 0.5;

    /**
     * rally-is-a-mob-effect-on-one-target: a mob ability at a flat cost, never a held area, cast at the one mob
     * its press took (charm-like-abilities-lock-the-highlighted-mob-on-right-click).
     */
    @Test
    void rallyShipsAsAMobAbilityCastAtOneTarget() {
        JsonObject rally = JsonParser.parseString(AbilityJson.read("zoo_rally")).getAsJsonObject();

        List<String> types = StreamSupport.stream(rally.getAsJsonArray("behaviors").spliterator(), false)
                .map(behavior -> behavior.getAsJsonObject().get("type").getAsString())
                .toList();

        assertEquals("mob", rally.get("badge").getAsString());
        assertEquals("envelop", rally.getAsJsonObject("delivery").get("kind").getAsString());
        assertTrue(rally.get("cost").getAsInt() > 0, "cost " + rally.get("cost"));
        assertFalse(rally.has("upkeep"), "upkeep " + rally.get("upkeep"));
        assertEquals(List.of("target"), types);
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

    /** rally-minions-get-half-your-armor-and-a-floor: half the rallier's armor, never under a floor above zero. */
    @Test
    void rallyStepLendsHalfTheRalliersArmorOverAFloor() {
        JsonObject step = JsonParser.parseString(AbilityJson.read("zoo_rally")).getAsJsonObject()
                .getAsJsonArray("behaviors").get(0).getAsJsonObject()
                .getAsJsonArray("steps").get(0).getAsJsonObject();

        assertTrue(step.has("armor_share") && step.has("armor_floor"), "rally step " + step);
        assertEquals(HALF, step.get("armor_share").getAsDouble());
        assertTrue(step.get("armor_floor").getAsDouble() > 0, "armor_floor " + step.get("armor_floor"));
    }

    @Test
    void rallyFistIsARedSixteenSquare() throws IOException {
        BufferedImage fist = read(RALLY_FIST);

        assertEquals(SIDE, fist.getWidth());
        assertEquals(SIDE, fist.getHeight());
        int body = fist.getRGB(FIST_BODY_X, FIST_BODY_Y);
        assertTrue((body >> RED_SHIFT & CHANNEL) > (body >> GREEN_SHIFT & CHANNEL) * 2);
    }

    private static BufferedImage read(String path) throws IOException {
        try (InputStream in = RallyShippedAssetsTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path + " is missing");
            return ImageIO.read(in);
        }
    }
}
