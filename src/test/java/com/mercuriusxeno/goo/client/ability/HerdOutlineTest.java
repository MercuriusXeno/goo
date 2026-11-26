package com.mercuriusxeno.goo.client.ability;

import com.google.gson.JsonParser;
import net.minecraft.util.ARGB;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Covers the outline a mob Herd senses wears: Yarn's highlight, opaque
 * while Herd stands, its brightness pulsing.
 * leader-outlines-every-mob-through-walls
 */
class HerdOutlineTest {

    private static final String YARN_TYPE = "/data/goo/goo/goo_type/yarn.json";
    private static final int OPAQUE = 0xFF;
    private static int yarnHighlight;

    @BeforeAll
    static void readYarnsHighlight() throws IOException {
        try (InputStream in = HerdOutlineTest.class.getResourceAsStream(YARN_TYPE)) {
            assertNotNull(in, YARN_TYPE + " is missing");
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                yarnHighlight = Integer.parseInt(
                        JsonParser.parseReader(reader).getAsJsonObject().get("highlight").getAsString(), 16);
            }
        }
    }

    @Test
    void outlineIsYarnsColor() {
        assertEquals(ARGB.opaque(yarnHighlight), HerdOutline.color(yarnHighlight, 0f, 1f));
        int dimmest = HerdOutline.color(yarnHighlight, PulsingOutline.PULSE_PERIOD / 2f, 1f);
        assertEquals(OPAQUE, ARGB.alpha(dimmest));
        assertEquals(PulsingOutline.color(yarnHighlight, PulsingOutline.PULSE_PERIOD / 2f), dimmest);
    }

    @Test
    void outlineBrightnessPulses() {
        assertNotEquals(ARGB.red(HerdOutline.color(yarnHighlight, 0f, 1f)),
                ARGB.red(HerdOutline.color(yarnHighlight, PulsingOutline.PULSE_PERIOD / 2f, 1f)));
    }
}
