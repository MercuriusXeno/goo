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
 * Goop's stream throws Goop's spray drip, the mod's blocky goo drip in slime greens.
 * goop-and-slick-spray-throws-goo-drips-that-splat
 * goop-sprays-a-slime-colored-mist
 */
class GoopSprayDripTest {

    private static final String GOOP_JSON = "/data/goo/goo_abilities/weird_goop.json";

    @Test
    void goopsStreamThrowsGoopsSprayDrip() throws IOException {
        try (InputStream in = GoopSprayDripTest.class.getResourceAsStream(GOOP_JSON)) {
            assertNotNull(in, GOOP_JSON);
            JsonObject goop = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("goo:goop_spray_drip", goop.getAsJsonObject("delivery").get("particle").getAsString());
        }
    }

    @Test
    void goopsSprayDripReadsGreenAtBothEndsOfItsBlend() {
        BlendPalette greens = BlendPalette.GOOP_SLIME_GREENS;

        assertTrue(greens.lightGreen() > greens.lightRed() && greens.lightGreen() > greens.lightBlue(),
                greens.toString());
        assertTrue(greens.darkGreen() > greens.darkRed() && greens.darkGreen() > greens.darkBlue(), greens.toString());
    }
}
