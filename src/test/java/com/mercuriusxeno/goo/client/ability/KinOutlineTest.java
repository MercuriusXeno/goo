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
 * Covers the outline a mob Kin senses wears: Zoo's highlight, opaque
 * while Kin stands, its brightness pulsing.
 * leader-outlines-every-mob-through-walls
 */
class KinOutlineTest {

    private static final String ZOO_TYPE = "/data/goo/goo/goo_type/zoo.json";
    private static final int OPAQUE = 0xFF;
    private static int zooHighlight;

    @BeforeAll
    static void readZoosHighlight() throws IOException {
        try (InputStream in = KinOutlineTest.class.getResourceAsStream(ZOO_TYPE)) {
            assertNotNull(in, ZOO_TYPE + " is missing");
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                zooHighlight = Integer.parseInt(
                        JsonParser.parseReader(reader).getAsJsonObject().get("highlight").getAsString(), 16);
            }
        }
    }

    @Test
    void outlineIsZoosColor() {
        assertEquals(ARGB.opaque(zooHighlight), KinOutline.color(zooHighlight, 0f, 1f));
        int dimmest = KinOutline.color(zooHighlight, PulsingOutline.PULSE_PERIOD / 2f, 1f);
        assertEquals(OPAQUE, ARGB.alpha(dimmest));
        assertEquals(PulsingOutline.color(zooHighlight, PulsingOutline.PULSE_PERIOD / 2f), dimmest);
    }

    @Test
    void outlineBrightnessPulses() {
        assertNotEquals(ARGB.red(KinOutline.color(zooHighlight, 0f, 1f)),
                ARGB.red(KinOutline.color(zooHighlight, PulsingOutline.PULSE_PERIOD / 2f, 1f)));
    }
}
