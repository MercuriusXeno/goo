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
 * Goop's stream sprays the slime mist, the gnat-stream particle decay's stream draws, in slime greens
 * (decision goop-sprays-a-slime-colored-mist).
 */
class SlimeMistTest {

    private static final String GOOP_JSON = "/data/goo/goo_abilities/weird_goop.json";

    @Test
    void goopsStreamSpraysTheSlimeMist() throws IOException {
        try (InputStream in = SlimeMistTest.class.getResourceAsStream(GOOP_JSON)) {
            assertNotNull(in, GOOP_JSON);
            JsonObject goop = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("goo:slime_mist", goop.getAsJsonObject("delivery").get("particle").getAsString());
        }
    }

    @Test
    void theSlimeMistReadsGreenAtBothEndsOfItsBlend() {
        GnatPalette mist = GnatPalette.SLIME_MIST;

        assertTrue(mist.lightGreen() > mist.lightRed() && mist.lightGreen() > mist.lightBlue(), mist.toString());
        assertTrue(mist.darkGreen() > mist.darkRed() && mist.darkGreen() > mist.darkBlue(), mist.toString());
    }
}
