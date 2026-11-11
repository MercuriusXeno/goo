package com.mercuriusxeno.goo.client.particle;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Slick's stream sprays the kinetic mist, the gnat-stream particle Goop's slime mist is, blending
 * Kinetic's white into its electric blue.
 * slick-coats-the-ground-with-kinetic-speed
 * kinetic-is-electric-blue-and-white
 */
class KineticMistTest {

    private static final String SLICK_JSON = "/data/goo/goo_abilities/kinetic_slick.json";
    private static final float NEAR_WHITE = 0.9f;

    @Test
    void slicksStreamSpraysTheKineticMist() throws IOException {
        try (InputStream in = KineticMistTest.class.getResourceAsStream(SLICK_JSON)) {
            assertNotNull(in, SLICK_JSON);
            JsonObject slick = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("goo:kinetic_mist", slick.getAsJsonObject("delivery").get("particle").getAsString());
        }
    }

    @Test
    void theKineticMistBlendsWhiteIntoElectricBlue() {
        GnatPalette mist = GnatPalette.KINETIC_MIST;

        assertTrue(mist.lightRed() > NEAR_WHITE && mist.lightGreen() > NEAR_WHITE && mist.lightBlue() > NEAR_WHITE,
                "the light end should read white, " + mist);
        assertTrue(mist.darkBlue() > mist.darkGreen() && mist.darkGreen() > mist.darkRed(),
                "the dark end should read electric blue, " + mist);
    }
}
